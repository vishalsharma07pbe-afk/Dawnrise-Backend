package com.dawnrise.identity.notification.service;
import com.dawnrise.identity.notification.dto.ResolveNotificationAudienceRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
@Service
public class NotificationAudienceResolver {
    private final NamedParameterJdbcTemplate jdbc;
    public NotificationAudienceResolver(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    public Set<Long> resolve(long organizationId,ResolveNotificationAudienceRequest request){
        if(organizationId<=0)throw new IllegalArgumentException("Organization is required");Set<Long> result=new HashSet<>();
        for(var audience:request.audiences()) switch(audience.type()) {
            case WHOLE_SCHOOL -> result.addAll(active(organizationId,null));
            case ROLE -> {if(audience.key()==null||!audience.key().matches("[A-Z_]{2,50}"))throw new IllegalArgumentException("Invalid role audience");result.addAll(active(organizationId,audience.key()));}
            case STAFF -> result.addAll(jdbc.queryForList("SELECT DISTINCT u.id FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.organization_id=:org AND u.status='ACTIVE' AND r.role NOT IN ('STUDENT','PARENT')",new MapSqlParameterSource("org",organizationId),Long.class));
            case STUDENT -> result.addAll(active(organizationId,"STUDENT"));
            case ACTIVE_GUARDIAN -> result.addAll(jdbc.queryForList("SELECT DISTINCT g.guardian_user_id FROM student_guardian_relationships g JOIN users u ON u.id=g.guardian_user_id AND u.organization_id=g.organization_id WHERE g.organization_id=:org AND g.status='ACTIVE' AND u.status='ACTIVE'",new MapSqlParameterSource("org",organizationId),Long.class));
        }
        return Set.copyOf(result);
    }
    private List<Long> active(long org,String role){if(role==null)return jdbc.queryForList("SELECT id FROM users WHERE organization_id=:org AND status='ACTIVE'",new MapSqlParameterSource("org",org),Long.class);return jdbc.queryForList("SELECT DISTINCT u.id FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.organization_id=:org AND u.status='ACTIVE' AND r.role=:role",new MapSqlParameterSource("org",org).addValue("role",role),Long.class);}
}
