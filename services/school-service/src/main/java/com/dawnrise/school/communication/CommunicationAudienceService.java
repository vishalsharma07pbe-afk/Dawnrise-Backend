package com.dawnrise.school.communication;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CommunicationAudienceService {
    private static final int MAX_PAGE_SIZE = 50;
    private final CommunicationDirectoryGateway gateway;
    public CommunicationAudienceService(CommunicationDirectoryGateway gateway) { this.gateway = gateway; }

    public CommunicationDtos.Page recipients(long org, long actor, boolean schoolWide, String query, int page, int size) {
        Set<Long> allowed = schoolWide ? null : scopedPeople(org, actor);
        var response = gateway.searchUsers(org, query, Math.max(0,page), Math.min(MAX_PAGE_SIZE,Math.max(1,size)), allowed, Set.of(actor));
        List<CommunicationDtos.Target> items = response == null ? List.of() : response.items().stream().map(user -> new CommunicationDtos.Target("INDIVIDUAL", Long.toString(user.id()), user.displayName(), String.join(", ", user.roles()))).toList();
        return new CommunicationDtos.Page(items, response == null ? Math.max(0,page) : response.page(), response == null ? Math.min(MAX_PAGE_SIZE,Math.max(1,size)) : response.size(), response == null ? 0 : response.total());
    }

    public CommunicationDtos.Page groups(long org,long actor,boolean schoolWide,String query,int page,int size) {
        List<CommunicationDtos.Target> all = new ArrayList<>();
        if (schoolWide) {
            all.add(new CommunicationDtos.Target("EVERYONE", null, "Everyone", "All active school users"));
            gateway.identityGroups(org).forEach(group -> all.add(new CommunicationDtos.Target(group.type(), group.key(), group.label(), group.detail())));
        }
        gateway.academicGroups(org,actor,schoolWide).forEach(group -> all.add(new CommunicationDtos.Target(group.type(), Long.toString(group.id()), group.label(), group.detail())));
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<CommunicationDtos.Target> filtered = all.stream().filter(item -> needle.isEmpty() || (item.label()+" "+item.detail()).toLowerCase(Locale.ROOT).contains(needle)).sorted(Comparator.comparing(CommunicationDtos.Target::label)).toList();
        int normalizedPage=Math.max(0,page), normalizedSize=Math.min(MAX_PAGE_SIZE,Math.max(1,size)), from=Math.min(filtered.size(),normalizedPage*normalizedSize), to=Math.min(filtered.size(),from+normalizedSize);
        return new CommunicationDtos.Page(filtered.subList(from,to),normalizedPage,normalizedSize,filtered.size());
    }

    public CommunicationDtos.Preview preview(long org,long actor,boolean schoolWide,CommunicationDtos.PreviewRequest request) {
        LinkedHashSet<String> normalizedKeys = new LinkedHashSet<>();
        List<CommunicationDtos.Selection> normalized = new ArrayList<>();
        LinkedHashSet<Long> recipients = new LinkedHashSet<>();
        Set<Long> scoped = schoolWide ? null : scopedPeople(org,actor);
        for (var selection : request.selections()) {
            String key = selection.key()==null ? null : selection.key().trim();
            String identity = selection.type()+":"+(key==null?"":key);
            if (!normalizedKeys.add(identity)) continue;
            Set<Long> resolved = resolve(org,actor,schoolWide,scoped,selection.type(),key);
            if (resolved.isEmpty()) throw new IllegalArgumentException("Selected audience is unavailable or empty");
            recipients.addAll(resolved); normalized.add(new CommunicationDtos.Selection(selection.type(),key));
        }
        List<CommunicationDtos.Channel> channels=request.channels()==null||request.channels().isEmpty()
                ?List.of(CommunicationDtos.Channel.APP):request.channels().stream().distinct().toList();
        List<CommunicationDtos.ChannelPreview> channelPreview=gateway.eligibility(org,Set.copyOf(recipients),channels.stream().map(Enum::name).collect(java.util.stream.Collectors.toSet())).stream().map(value->new CommunicationDtos.ChannelPreview(CommunicationDtos.Channel.valueOf(value.channel()),value.eligibleCount(),value.excludedCount(),value.available(),value.reason())).toList();
        return new CommunicationDtos.Preview(List.copyOf(normalized),recipients.size(),channelPreview,"AT_DELIVERY_TIME","Scheduled audience membership will be resolved again at delivery time.");
    }

    public Set<Long> resolveRecipients(long org,long actor,boolean schoolWide,List<CommunicationDtos.Selection> selections) {
        if (selections == null || selections.isEmpty() || selections.size() > 50) throw new IllegalArgumentException("Between 1 and 50 audiences are required");
        LinkedHashSet<Long> recipients=new LinkedHashSet<>();
        LinkedHashSet<String> seen=new LinkedHashSet<>();
        Set<Long> scoped=schoolWide?null:scopedPeople(org,actor);
        for(var selection:selections){String key=selection.key()==null?null:selection.key().trim();if(!seen.add(selection.type()+":"+(key==null?"":key)))continue;recipients.addAll(resolve(org,actor,schoolWide,scoped,selection.type(),key));}
        if(recipients.isEmpty())throw new IllegalArgumentException("Selected audience is unavailable or empty");
        return Set.copyOf(recipients);
    }

    public boolean requiresSchoolWide(List<CommunicationDtos.Selection> selections) {
        return selections.stream().anyMatch(selection -> Set.of(CommunicationDtos.Type.EVERYONE,CommunicationDtos.Type.STAFF,CommunicationDtos.Type.STUDENT,CommunicationDtos.Type.ACTIVE_GUARDIAN,CommunicationDtos.Type.ROLE).contains(selection.type()));
    }

    private Set<Long> resolve(long org,long actor,boolean schoolWide,Set<Long> scoped,CommunicationDtos.Type type,String key) {
        return switch(type) {
            case INDIVIDUAL -> { long id=positive(key); if (!schoolWide && !scoped.contains(id)) throw new IllegalArgumentException("Recipient is outside the actor assignment scope"); yield gateway.resolveUsers(org,Set.of(id)); }
            case EVERYONE -> { requireSchoolWide(schoolWide); requireNoKey(key); yield gateway.identityAudience(org,"WHOLE_SCHOOL",null); }
            case STAFF,STUDENT,ACTIVE_GUARDIAN,ROLE -> { requireSchoolWide(schoolWide); if(type==CommunicationDtos.Type.ROLE && (key==null||!key.matches("[A-Z_]{2,50}"))) throw new IllegalArgumentException("Invalid role audience"); if(type!=CommunicationDtos.Type.ROLE) requireNoKey(key); yield gateway.identityAudience(org,type.name(),key); }
            case SECTION,GRADE,WING -> gateway.academicAudience(org,actor,schoolWide,type.name(),positive(key));
            case SECTION_FAMILIES,GRADE_FAMILIES,WING_FAMILIES -> gateway.guardians(org,gateway.academicAudience(org,actor,schoolWide,type.name(),positive(key)));
        };
    }
    private Set<Long> scopedPeople(long org,long actor) {
        Set<Long> students=gateway.scopeStudents(org,actor,false);
        LinkedHashSet<Long> people=new LinkedHashSet<>(gateway.identityAudience(org,"STAFF",null));
        people.addAll(students);
        if(!students.isEmpty()) people.addAll(gateway.guardians(org,students));
        return Set.copyOf(people);
    }
    private static long positive(String key){try{long value=Long.parseLong(key);if(value<=0)throw new NumberFormatException();return value;}catch(Exception e){throw new IllegalArgumentException("A positive audience identifier is required");}}
    private static void requireNoKey(String key){if(key!=null&&!key.isBlank())throw new IllegalArgumentException("This audience does not accept an identifier");}
    private static void requireSchoolWide(boolean allowed){if(!allowed)throw new IllegalArgumentException("School-wide audience permission is required");}
}
