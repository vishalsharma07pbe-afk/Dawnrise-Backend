package com.dawnrise.school.leave;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class LeaveCoverageOutboxStore {
    private final NamedParameterJdbcTemplate jdbc;
    public LeaveCoverageOutboxStore(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional public Optional<Row> claim(UUID owner,Duration lease){var params=new MapSqlParameterSource("owner",owner).addValue("lease",lease.toSeconds());return jdbc.query("""
            WITH candidate AS (SELECT id FROM staff_leave_coverage_outbox WHERE status='PENDING'
            AND available_at<=CURRENT_TIMESTAMP AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)
            ORDER BY available_at,id FOR UPDATE SKIP LOCKED LIMIT 1)
            UPDATE staff_leave_coverage_outbox o SET lease_owner=:owner,lease_until=CURRENT_TIMESTAMP+(:lease*INTERVAL '1 second'),updated_at=CURRENT_TIMESTAMP
            FROM candidate c WHERE o.id=c.id RETURNING o.*
            """,params,(rs,n)->new Row(rs.getLong("id"),rs.getLong("organization_id"),rs.getLong("leave_request_id"),rs.getString("operation"),rs.getString("source_event_id"),rs.getLong("actor_user_id"),rs.getString("payload"),rs.getInt("attempt_count"))).stream().findFirst();}
    @Transactional public void published(long id,UUID owner){jdbc.update("UPDATE staff_leave_coverage_outbox SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP,lease_owner=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND lease_owner=:owner",new MapSqlParameterSource("id",id).addValue("owner",owner));audit(id,"PUBLISHED",null,null);}
    @Transactional public void failed(long id,UUID owner,int attempts,int max,String error){long delay=Math.min(3600,15L*(1L<<Math.min(attempts,7)));jdbc.update("UPDATE staff_leave_coverage_outbox SET status=CASE WHEN :attempts>=:max THEN 'DEAD_LETTER' ELSE 'PENDING' END,attempt_count=:attempts,available_at=CURRENT_TIMESTAMP+(:delay*INTERVAL '1 second'),lease_owner=NULL,lease_until=NULL,last_error=:error,dead_lettered_at=CASE WHEN :attempts>=:max THEN CURRENT_TIMESTAMP ELSE NULL END,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND lease_owner=:owner",new MapSqlParameterSource("id",id).addValue("owner",owner).addValue("attempts",attempts).addValue("max",max).addValue("delay",delay).addValue("error",safe(error)));audit(id,attempts>=max?"DEAD_LETTERED":"FAILED",null,error);}
    @Transactional(readOnly=true) public List<DeadLetter> deadLetters(long org){return jdbc.query("SELECT id,leave_request_id,operation,attempt_count,last_error,dead_lettered_at FROM staff_leave_coverage_outbox WHERE organization_id=:org AND status='DEAD_LETTER' ORDER BY dead_lettered_at DESC",new MapSqlParameterSource("org",org),(rs,n)->new DeadLetter(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getInt(4),rs.getString(5),rs.getObject(6,java.time.OffsetDateTime.class)));}
    @Transactional public boolean requeue(long org,long id,long actor,String reason){int changed=jdbc.update("UPDATE staff_leave_coverage_outbox SET status='PENDING',attempt_count=0,available_at=CURRENT_TIMESTAMP,dead_lettered_at=NULL,last_error=NULL,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND id=:id AND status='DEAD_LETTER'",new MapSqlParameterSource("org",org).addValue("id",id));if(changed==1)audit(id,"REQUEUED",actor,reason);return changed==1;}
    private void audit(long id,String action,Long actor,String detail){jdbc.update("INSERT INTO staff_leave_coverage_delivery_audit(organization_id,outbox_id,action,actor_user_id,detail) SELECT organization_id,id,:action,:actor,:detail FROM staff_leave_coverage_outbox WHERE id=:id",new MapSqlParameterSource("id",id).addValue("action",action).addValue("actor",actor).addValue("detail",detail==null?null:safe(detail)));}
    private String safe(String value){if(value==null)return "Unknown delivery failure";return value.substring(0,Math.min(1000,value.length()));}
    public record Row(long id,long organizationId,long leaveRequestId,String operation,String sourceEventId,long actorUserId,String payload,int attemptCount){}
    public record DeadLetter(long id,long leaveRequestId,String operation,int attempts,String error,java.time.OffsetDateTime deadLetteredAt){}
}
