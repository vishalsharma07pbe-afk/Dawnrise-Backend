package com.dawnrise.identity.notification.service;

import com.dawnrise.identity.notification.dto.CommunicationIdentityDtos;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.dawnrise.identity.notification.sms.NotificationSmsProperties;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class CommunicationIdentityDirectory {
    private static final int MAX_PAGE_SIZE = 50;
    private final NamedParameterJdbcTemplate jdbc;
    private final NotificationSmsProperties sms;

    @Autowired
    public CommunicationIdentityDirectory(NamedParameterJdbcTemplate jdbc,NotificationSmsProperties sms) { this.jdbc = jdbc;this.sms=sms; }
    CommunicationIdentityDirectory(NamedParameterJdbcTemplate jdbc) { this(jdbc,new NotificationSmsProperties()); }

    public CommunicationIdentityDtos.Page search(long organizationId, CommunicationIdentityDtos.SearchRequest request) {
        requireOrganization(organizationId);
        int page = Math.max(0, request.page());
        int size = Math.min(MAX_PAGE_SIZE, Math.max(1, request.size()));
        String query = request.query() == null ? "" : request.query().trim().toLowerCase();
        Set<Long> allowed = request.allowedUserIds();
        Set<Long> excluded = request.excludedUserIds();
        if (allowed != null && allowed.isEmpty()) return new CommunicationIdentityDtos.Page(List.of(), page, size, 0);
        String allowedClause = allowed == null ? "" : " AND u.id IN (:allowed)";
        String excludedClause = excluded == null || excluded.isEmpty() ? "" : " AND u.id NOT IN (:excluded)";
        var parameters = new MapSqlParameterSource("organizationId", organizationId)
                .addValue("query", "%" + query + "%")
                .addValue("limit", size).addValue("offset", page * size);
        if (allowed != null) parameters.addValue("allowed", allowed);
        if (excluded != null && !excluded.isEmpty()) parameters.addValue("excluded", excluded);
        String filter = " FROM users u WHERE u.organization_id=:organizationId AND u.status='ACTIVE' " +
                "AND (:query='%%' OR LOWER(CONCAT_WS(' ',u.first_name,u.middle_name,u.last_name)) LIKE :query)" + allowedClause + excludedClause;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + filter, parameters, Long.class);
        List<CommunicationIdentityDtos.User> users = jdbc.query(
                "SELECT u.id, TRIM(CONCAT_WS(' ',u.first_name,u.middle_name,u.last_name)) display_name, " +
                        "COALESCE(STRING_AGG(DISTINCT r.role, ',' ORDER BY r.role),'') roles " +
                        "FROM users u LEFT JOIN user_roles r ON r.user_id=u.id " +
                        "WHERE u.organization_id=:organizationId AND u.status='ACTIVE' " +
                        "AND (:query='%%' OR LOWER(CONCAT_WS(' ',u.first_name,u.middle_name,u.last_name)) LIKE :query)" + allowedClause + excludedClause +
                        " GROUP BY u.id ORDER BY display_name,u.id LIMIT :limit OFFSET :offset",
                parameters, (rs, row) -> new CommunicationIdentityDtos.User(rs.getLong("id"), rs.getString("display_name"),
                        rs.getString("roles").isBlank() ? List.of() : List.of(rs.getString("roles").split(","))));
        return new CommunicationIdentityDtos.Page(users, page, size, total == null ? 0 : total);
    }

    public Set<Long> resolveActiveUsers(long organizationId, Set<Long> ids) {
        requireOrganization(organizationId);
        if (ids == null || ids.isEmpty() || ids.size() > 500) throw new IllegalArgumentException("Between 1 and 500 user identifiers are required");
        Set<Long> result = new LinkedHashSet<>(jdbc.queryForList(
                "SELECT id FROM users WHERE organization_id=:organizationId AND status='ACTIVE' AND id IN (:ids) ORDER BY id",
                new MapSqlParameterSource("organizationId", organizationId).addValue("ids", ids), Long.class));
        if (result.size() != ids.size()) throw new IllegalArgumentException("One or more recipients are unavailable");
        return Set.copyOf(result);
    }

    public List<CommunicationIdentityDtos.Group> groups(long organizationId) {
        requireOrganization(organizationId);
        List<CommunicationIdentityDtos.Group> result = new java.util.ArrayList<>();
        result.add(new CommunicationIdentityDtos.Group("STAFF", null, "All staff", "Active staff users"));
        result.add(new CommunicationIdentityDtos.Group("STUDENT", null, "All students", "Active student users"));
        result.add(new CommunicationIdentityDtos.Group("ACTIVE_GUARDIAN", null, "All active guardians", "Active linked guardians"));
        result.addAll(jdbc.query("SELECT DISTINCT r.role FROM user_roles r JOIN users u ON u.id=r.user_id WHERE u.organization_id=:organizationId AND u.status='ACTIVE' ORDER BY r.role",
                new MapSqlParameterSource("organizationId", organizationId),
                (rs, row) -> new CommunicationIdentityDtos.Group("ROLE", rs.getString("role"), rs.getString("role").replace('_', ' '), "Active users with this role")));
        return List.copyOf(result);
    }

    public Set<Long> activeGuardians(long organizationId, Set<Long> studentIds) {
        requireOrganization(organizationId);
        if (studentIds == null || studentIds.isEmpty() || studentIds.size() > 5000) throw new IllegalArgumentException("Between 1 and 5000 student identifiers are required");
        return Set.copyOf(jdbc.queryForList("SELECT DISTINCT g.guardian_user_id FROM student_guardian_relationships g " +
                        "JOIN users u ON u.id=g.guardian_user_id AND u.organization_id=g.organization_id " +
                        "WHERE g.organization_id=:organizationId AND g.student_user_id IN (:students) " +
                        "AND g.status='ACTIVE' AND u.status='ACTIVE' ORDER BY g.guardian_user_id",
                new MapSqlParameterSource("organizationId", organizationId).addValue("students", studentIds), Long.class));
    }

    public CommunicationIdentityDtos.Authority authority(long organizationId, long userId) {
        requireOrganization(organizationId);
        if (userId <= 0) throw new IllegalArgumentException("User is required");
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE organization_id=:organizationId AND id=:userId AND status='ACTIVE'",
                new MapSqlParameterSource("organizationId", organizationId).addValue("userId", userId), Integer.class);
        if (active == null || active != 1) return new CommunicationIdentityDtos.Authority(false, Set.of());
        Set<String> permissions = Set.copyOf(jdbc.queryForList("SELECT DISTINCT p.code FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN role_permissions rp ON rp.role=ur.role JOIN permissions p ON p.id=rp.permission_id WHERE u.organization_id=:organizationId AND u.id=:userId AND u.status='ACTIVE' AND p.active=TRUE ORDER BY p.code",
                new MapSqlParameterSource("organizationId", organizationId).addValue("userId", userId), String.class));
        return new CommunicationIdentityDtos.Authority(true, permissions);
    }

    public CommunicationIdentityDtos.Eligibility eligibility(long organizationId,CommunicationIdentityDtos.EligibilityRequest request) {
        requireOrganization(organizationId);
        Set<Long> active=Set.copyOf(jdbc.queryForList("SELECT id FROM users WHERE organization_id=:org AND status='ACTIVE' AND id IN (:ids)",
                new MapSqlParameterSource("org",organizationId).addValue("ids",request.userIds()),Long.class));
        int total=request.userIds().size();
        List<CommunicationIdentityDtos.ChannelEligibility> result=request.channels().stream().sorted().map(channel->switch(channel){
            case "APP" -> new CommunicationIdentityDtos.ChannelEligibility(channel,active.size(),total-active.size(),true,null);
            case "EMAIL" -> countEligibility(organizationId,active,channel,"u.email IS NOT NULL AND BTRIM(u.email)<>'' AND COALESCE(p.school_email_mode,'IMMEDIATE')<>'DISABLED'",true,null);
            case "SMS" -> sms.configured()?countEligibility(organizationId,active,channel,"u.phone ~ '^\\+[1-9][0-9]{7,14}$' AND COALESCE(p.school_sms_enabled,FALSE)=TRUE",true,null):new CommunicationIdentityDtos.ChannelEligibility(channel,0,total,false,"SMS provider and approved template setup is incomplete");
            case "WHATSAPP" -> new CommunicationIdentityDtos.ChannelEligibility(channel,0,total,false,"WhatsApp Business provider and approved template setup is required");
            case "CALL" -> new CommunicationIdentityDtos.ChannelEligibility(channel,0,total,false,"Voice provider configuration and consent rules are required");
            default -> throw new IllegalArgumentException("Unsupported delivery channel");
        }).toList();
        return new CommunicationIdentityDtos.Eligibility(result);
    }

    private CommunicationIdentityDtos.ChannelEligibility countEligibility(long org,Set<Long> active,String channel,String condition,boolean available,String reason){
        if(active.isEmpty())return new CommunicationIdentityDtos.ChannelEligibility(channel,0,0,available,reason);
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM users u LEFT JOIN notification_preferences p ON p.organization_id=u.organization_id AND p.user_id=u.id WHERE u.organization_id=:org AND u.id IN (:ids) AND "+condition,new MapSqlParameterSource("org",org).addValue("ids",active),Integer.class);
        int eligible=count==null?0:count;return new CommunicationIdentityDtos.ChannelEligibility(channel,eligible,active.size()-eligible,available,reason);
    }

    private void requireOrganization(long organizationId) { if (organizationId <= 0) throw new IllegalArgumentException("Organization is required"); }
}
