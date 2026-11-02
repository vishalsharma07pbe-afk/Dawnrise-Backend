package com.dawnrise.identity.user.controller;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/internal/v1/organizations/{organizationId}/staff-directory")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalStaffDirectoryController {
    private static final Set<String> NON_STAFF = Set.of("STUDENT", "PARENT");
    private final NamedParameterJdbcTemplate jdbc;

    public InternalStaffDirectoryController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{userId}")
    public StaffView staff(@PathVariable long organizationId, @PathVariable long userId) {
        var parameters = new MapSqlParameterSource("organizationId", organizationId).addValue("userId", userId);
        var rows = jdbc.query("""
                SELECT id,status,CONCAT_WS(' ',first_name,NULLIF(middle_name,''),NULLIF(last_name,'')) display_name
                FROM users WHERE organization_id=:organizationId AND id=:userId
                """, parameters, (rs, row) -> new StaffView(rs.getLong("id"), rs.getString("status"),
                rs.getString("display_name"), roles(userId)));
        if (rows.isEmpty() || rows.getFirst().roles().isEmpty()
                || rows.getFirst().roles().stream().allMatch(NON_STAFF::contains)) {
            return new StaffView(userId, "NOT_FOUND", "", Set.of());
        }
        return rows.getFirst();
    }

    @GetMapping("/approvers")
    public List<StaffView> approvers(@PathVariable long organizationId,
                                     @RequestParam List<String> roles) {
        if (roles.isEmpty()) return List.of();
        var parameters = new MapSqlParameterSource("organizationId", organizationId).addValue("roles", roles);
        return jdbc.query("""
                SELECT DISTINCT u.id,u.status,CONCAT_WS(' ',u.first_name,NULLIF(u.middle_name,''),NULLIF(u.last_name,'')) display_name
                FROM users u JOIN user_roles r ON r.user_id=u.id
                WHERE u.organization_id=:organizationId AND u.status='ACTIVE' AND r.role IN (:roles)
                ORDER BY u.id
                """, parameters, (rs, row) -> new StaffView(rs.getLong("id"), rs.getString("status"),
                rs.getString("display_name"), roles(rs.getLong("id"))));
    }

    private Set<String> roles(long userId) {
        return new LinkedHashSet<>(jdbc.queryForList("SELECT role FROM user_roles WHERE user_id=:userId ORDER BY role",
                new MapSqlParameterSource("userId", userId), String.class));
    }

    public record StaffView(long userId, String status, String displayName, Set<String> roles) {}
}
