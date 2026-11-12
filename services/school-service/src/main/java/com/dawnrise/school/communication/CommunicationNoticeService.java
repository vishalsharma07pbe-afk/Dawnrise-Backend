package com.dawnrise.school.communication;

import com.dawnrise.school.school.exception.ResourceNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

@Service
public class CommunicationNoticeService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CommunicationAudienceService audiences;
    public CommunicationNoticeService(NamedParameterJdbcTemplate jdbc,ObjectMapper json,CommunicationAudienceService audiences){this.jdbc=jdbc;this.json=json;this.audiences=audiences;}

    @Transactional
    public CommunicationDtos.Notice create(long org,long actor,boolean schoolWide,String idempotencyKey,CommunicationDtos.CreateNotice request){
        String key=key(idempotencyKey),subject=plain(request.subject(),120),body=plain(request.body(),4000);
        List<CommunicationDtos.Channel> channels=channels(request.channels());
        var preview=audiences.preview(org,actor,schoolWide,new CommunicationDtos.PreviewRequest(request.selections(),channels));
        if(preview.channels().stream().anyMatch(channel->!channel.available()))throw new IllegalArgumentException("One or more selected delivery channels are not configured");
        if(preview.channels().stream().noneMatch(channel->channel.eligibleCount()>0))throw new IllegalArgumentException("Selected channels have no eligible recipients");
        String fingerprint=fingerprint(subject,body,List.of(preview.selections(),channels));
        Long id=jdbc.query("INSERT INTO school_announcements(organization_id,title,body,severity,status,created_by_user_id,communication_notice,idempotency_key,request_fingerprint,delivery_channels) VALUES(:org,:subject,:body,'NORMAL','DRAFT',:actor,TRUE,:key,:fingerprint,CAST(:channels AS JSONB)) ON CONFLICT(organization_id,created_by_user_id,idempotency_key) WHERE communication_notice DO NOTHING RETURNING id",
                params(org,actor).addValue("subject",subject).addValue("body",body).addValue("key",key).addValue("fingerprint",fingerprint).addValue("channels",write(channels)),(rs,n)->rs.getLong(1)).stream().findFirst().orElse(null);
        if(id==null){var existing=jdbc.queryForMap("SELECT id,request_fingerprint FROM school_announcements WHERE organization_id=:org AND created_by_user_id=:actor AND idempotency_key=:key AND communication_notice",params(org,actor).addValue("key",key));if(!fingerprint.equals(existing.get("request_fingerprint")))throw new IllegalStateException("Idempotency key is already associated with different notice content");id=((Number)existing.get("id")).longValue();return withCount(get(org,actor,id),preview.recipientCount());}
        for(var selection:preview.selections())jdbc.update("INSERT INTO school_announcement_audiences(organization_id,announcement_id,audience_type,audience_key) VALUES(:org,:id,:type,:key)",new MapSqlParameterSource("org",org).addValue("id",id).addValue("type",dbType(selection.type())).addValue("key",selection.key()));
        audit(org,id,actor,"CREATED");
        return withCount(get(org,actor,id),preview.recipientCount());
    }

    @Transactional
    public CommunicationDtos.Notice publish(long org,long actor,boolean schoolWide,long id,String idempotencyKey,long version){
        var current=get(org,actor,id);String fp=fingerprint("PUBLISH",Long.toString(version),current.selections());
        if(replayed(org,actor,id,"PUBLISH",idempotencyKey,fp))return get(org,actor,id);
        if(!List.of("DRAFT","SCHEDULED").contains(current.status()))throw new IllegalStateException("Notice cannot be published");
        var preview=audiences.preview(org,actor,schoolWide,new CommunicationDtos.PreviewRequest(current.selections(),current.channels()));
        if(preview.recipientCount()==0)throw new IllegalArgumentException("Notice has no eligible recipients");
        if(jdbc.update("UPDATE school_announcements SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP,scheduled_at=NULL,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE organization_id=:org AND id=:id AND created_by_user_id=:actor AND version=:version",params(org,actor).addValue("id",id).addValue("version",version))!=1)throw new OptimisticLockingFailureException("Notice changed while this page was open");
        enqueue(org,actor,id,current.subject(),current.body(),current.selections(),current.channels(),schoolWide,OffsetDateTime.now());audit(org,id,actor,"PUBLISHED");return get(org,actor,id);
    }

    @Transactional
    public CommunicationDtos.Notice schedule(long org,long actor,boolean schoolWide,long id,String idempotencyKey,CommunicationDtos.ScheduleMutation request){
        String zone=jdbc.queryForObject("SELECT time_zone_id FROM schools WHERE id=:org",new MapSqlParameterSource("org",org),String.class);OffsetDateTime scheduled=request.scheduledLocal().atZone(ZoneId.of(zone)).toOffsetDateTime();
        if(!scheduled.isAfter(OffsetDateTime.now()))throw new IllegalArgumentException("Scheduled time must be in the future in the school time zone");
        var current=get(org,actor,id);String fp=fingerprint("SCHEDULE",scheduled+":"+request.version(),current.selections());
        if(replayed(org,actor,id,"SCHEDULE",idempotencyKey,fp))return get(org,actor,id);
        if(!List.of("DRAFT","SCHEDULED").contains(current.status()))throw new IllegalStateException("Notice cannot be scheduled");
        var preview=audiences.preview(org,actor,schoolWide,new CommunicationDtos.PreviewRequest(current.selections(),current.channels()));if(preview.recipientCount()==0)throw new IllegalArgumentException("Notice has no eligible recipients");
        if(jdbc.update("UPDATE school_announcements SET status='SCHEDULED',scheduled_at=:scheduled,published_at=NULL,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE organization_id=:org AND id=:id AND created_by_user_id=:actor AND version=:version",params(org,actor).addValue("id",id).addValue("version",request.version()).addValue("scheduled",scheduled))!=1)throw new OptimisticLockingFailureException("Notice changed while this page was open");
        enqueue(org,actor,id,current.subject(),current.body(),current.selections(),current.channels(),schoolWide,scheduled);audit(org,id,actor,"SCHEDULED");return get(org,actor,id);
    }

    @Transactional
    public CommunicationDtos.Notice cancel(long org,long actor,long id,String idempotencyKey,long version){
        var current=get(org,actor,id);String fp=fingerprint("CANCEL",Long.toString(version),current.selections());if(replayed(org,actor,id,"CANCEL",idempotencyKey,fp))return get(org,actor,id);
        if(!List.of("DRAFT","SCHEDULED").contains(current.status()))throw new IllegalStateException("Only an undelivered draft or scheduled notice can be cancelled");
        Integer inFlight=jdbc.queryForObject("SELECT COUNT(*) FROM school_notification_outbox WHERE organization_id=:org AND source_type='ANNOUNCEMENT' AND source_id=:id AND (lease_owner IS NOT NULL OR recipient_set_accepted_at IS NOT NULL OR status='PUBLISHED')",new MapSqlParameterSource("org",org).addValue("id",id),Integer.class);if(inFlight!=null&&inFlight>0)throw new IllegalStateException("Notice delivery has already been accepted and cannot be cancelled");
        if(jdbc.update("UPDATE school_announcements SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE organization_id=:org AND id=:id AND created_by_user_id=:actor AND version=:version",params(org,actor).addValue("id",id).addValue("version",version))!=1)throw new OptimisticLockingFailureException("Notice changed while this page was open");
        jdbc.update("UPDATE school_notification_outbox SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND source_type='ANNOUNCEMENT' AND source_id=:id AND status='PENDING' AND lease_owner IS NULL AND recipient_set_accepted_at IS NULL",new MapSqlParameterSource("org",org).addValue("id",id));audit(org,id,actor,"CANCELLED");return get(org,actor,id);
    }

    @Transactional(readOnly=true) public CommunicationDtos.Notice get(long org,long actor,long id){
        var rows=jdbc.query("SELECT a.*,o.status delivery_status,o.accepted_recipient_count,o.last_error FROM school_announcements a LEFT JOIN school_notification_outbox o ON o.organization_id=a.organization_id AND o.source_type='ANNOUNCEMENT' AND o.source_id=a.id WHERE a.organization_id=:org AND a.id=:id AND a.created_by_user_id=:actor AND a.communication_notice",params(org,actor).addValue("id",id),(rs,n)->new CommunicationDtos.Notice(rs.getLong("id"),rs.getString("title"),rs.getString("body"),rs.getString("status"),delivery(rs.getString("status"),rs.getString("delivery_status")),selections(org,id),readChannels(rs.getString("delivery_channels")),rs.getString("voice_script"),0,(Integer)rs.getObject("accepted_recipient_count"),rs.getObject("scheduled_at",OffsetDateTime.class),rs.getObject("published_at",OffsetDateTime.class),rs.getObject("created_at",OffsetDateTime.class),rs.getLong("version"),rs.getString("last_error")));
        if(rows.isEmpty())throw new ResourceNotFoundException("Communication notice not found");return rows.getFirst();
    }

    @Transactional(readOnly=true) public CommunicationDtos.NoticePage list(long org,long actor,int page,int size){int p=Math.max(0,page),s=Math.min(50,Math.max(1,size));var ids=jdbc.queryForList("SELECT id FROM school_announcements WHERE organization_id=:org AND created_by_user_id=:actor AND communication_notice ORDER BY created_at DESC,id DESC LIMIT :size OFFSET :offset",params(org,actor).addValue("size",s).addValue("offset",p*s),Long.class);Long total=jdbc.queryForObject("SELECT COUNT(*) FROM school_announcements WHERE organization_id=:org AND created_by_user_id=:actor AND communication_notice",params(org,actor),Long.class);return new CommunicationDtos.NoticePage(ids.stream().map(id->get(org,actor,id)).toList(),p,s,total==null?0:total);}

    private CommunicationDtos.Notice withCount(CommunicationDtos.Notice value,int count){return new CommunicationDtos.Notice(value.id(),value.subject(),value.body(),value.status(),value.deliveryStatus(),value.selections(),value.channels(),value.voiceScript(),count,value.acceptedRecipientCount(),value.scheduledAt(),value.publishedAt(),value.createdAt(),value.version(),value.lastError());}
    private List<CommunicationDtos.Selection> selections(long org,long id){return jdbc.query("SELECT audience_type,audience_key FROM school_announcement_audiences WHERE organization_id=:org AND announcement_id=:id ORDER BY id",new MapSqlParameterSource("org",org).addValue("id",id),(rs,n)->new CommunicationDtos.Selection(apiType(rs.getString(1)),rs.getString(2)));}
    private void enqueue(long org,long actor,long id,String subject,String body,List<CommunicationDtos.Selection> selections,List<CommunicationDtos.Channel> channels,boolean schoolWide,OffsetDateTime available){try{int changed=jdbc.update("INSERT INTO school_notification_outbox(source_type,source_id,organization_id,source_event_id,event_type,importance,heading,context,audience_spec,delivery_channels,available_at,sender_user_id,school_wide_required) VALUES('ANNOUNCEMENT',:id,:org,:event,'SCHOOL_ANNOUNCEMENT','NORMAL',:heading,:context,CAST(:audience AS JSONB),CAST(:channels AS JSONB),:available,:actor,:schoolWide) ON CONFLICT(organization_id,source_event_id) DO UPDATE SET status='PENDING',available_at=EXCLUDED.available_at,heading=EXCLUDED.heading,context=EXCLUDED.context,audience_spec=EXCLUDED.audience_spec,delivery_channels=EXCLUDED.delivery_channels,sender_user_id=EXCLUDED.sender_user_id,school_wide_required=EXCLUDED.school_wide_required,last_error=NULL,updated_at=CURRENT_TIMESTAMP WHERE school_notification_outbox.status IN ('PENDING','CANCELLED') AND school_notification_outbox.recipient_set_accepted_at IS NULL",params(org,actor).addValue("id",id).addValue("event","communication-notice:"+id).addValue("heading",subject).addValue("context",body.length()>500?body.substring(0,500):body).addValue("audience",json.writeValueAsString(selections)).addValue("channels",write(channels)).addValue("available",available).addValue("schoolWide",schoolWide));if(changed!=1)throw new IllegalStateException("Notice delivery has already been accepted");}catch(IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("Notice delivery could not be queued",e);}}
    private List<CommunicationDtos.Channel> channels(List<CommunicationDtos.Channel> requested){return requested==null||requested.isEmpty()?List.of(CommunicationDtos.Channel.APP):requested.stream().distinct().toList();}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Notice channels could not be serialized",e);}}
    private List<CommunicationDtos.Channel> readChannels(String value){try{return json.readValue(value,new TypeReference<List<CommunicationDtos.Channel>>(){});}catch(Exception e){throw new IllegalStateException("Notice channels could not be read",e);}}
    private boolean replayed(long org,long actor,long id,String action,String key,String fingerprint){String normalized=key(key);var inserted=jdbc.update("INSERT INTO communication_notice_mutations(organization_id,announcement_id,actor_user_id,action,idempotency_key,request_fingerprint) VALUES(:org,:id,:actor,:action,:key,:fingerprint) ON CONFLICT DO NOTHING",params(org,actor).addValue("id",id).addValue("action",action).addValue("key",normalized).addValue("fingerprint",fingerprint));if(inserted==1)return false;var row=jdbc.queryForMap("SELECT announcement_id,request_fingerprint FROM communication_notice_mutations WHERE organization_id=:org AND actor_user_id=:actor AND action=:action AND idempotency_key=:key",params(org,actor).addValue("action",action).addValue("key",normalized));if(((Number)row.get("announcement_id")).longValue()!=id||!fingerprint.equals(row.get("request_fingerprint")))throw new IllegalStateException("Idempotency key is already associated with a different mutation");return true;}
    private String fingerprint(String one,String two,Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((one+'\n'+two+'\n'+json.writeValueAsString(value)).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("Notice fingerprint could not be created",e);}}
    private String key(String value){if(value==null||!value.matches("[A-Za-z0-9._:-]{8,120}"))throw new IllegalArgumentException("A valid Idempotency-Key header is required");return value;}
    private String plain(String value,int max){String normalized=value==null?"":value.trim();if(normalized.isEmpty()||normalized.length()>max||normalized.contains("<")||normalized.contains(">"))throw new IllegalArgumentException("Notice text is invalid");return normalized;}
    private boolean schoolWide(List<CommunicationDtos.Selection> value){return audiences.requiresSchoolWide(value);}
    private String dbType(CommunicationDtos.Type type){return type==CommunicationDtos.Type.EVERYONE?"WHOLE_SCHOOL":type.name();}
    private CommunicationDtos.Type apiType(String type){return "WHOLE_SCHOOL".equals(type)?CommunicationDtos.Type.EVERYONE:CommunicationDtos.Type.valueOf(type);}
    private String delivery(String status,String outbox){if("CANCELLED".equals(status))return "CANCELLED";if("EXPIRED".equals(status))return "EXPIRED";if(outbox==null)return "DRAFT";return switch(outbox){case "PENDING"->"QUEUED";case "PUBLISHED"->"ACCEPTED";case "DEAD_LETTER"->"FAILED";case "CANCELLED"->"CANCELLED";default->outbox;};}
    private MapSqlParameterSource params(long org,long actor){return new MapSqlParameterSource("org",org).addValue("actor",actor);}
    private void audit(long org,long id,long actor,String action){jdbc.update("INSERT INTO school_announcement_audit(organization_id,announcement_id,actor_user_id,action) VALUES(:org,:id,:actor,:action)",params(org,actor).addValue("id",id).addValue("action",action));}
}
