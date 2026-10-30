package com.dawnrise.identity.studentguardian.service;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ActiveGuardianRecipientService {
    private final NamedParameterJdbcTemplate jdbc;

    public ActiveGuardianRecipientService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Long> activeGuardianUserIds(long organizationId, long studentUserId) {
        return jdbc.queryForList("""
                SELECT DISTINCT relationship.guardian_user_id
                  FROM student_guardian_relationships relationship
                  JOIN users student
                    ON student.id=relationship.student_user_id
                   AND student.organization_id=relationship.organization_id
                  JOIN users guardian
                    ON guardian.id=relationship.guardian_user_id
                   AND guardian.organization_id=relationship.organization_id
                 WHERE relationship.organization_id=:organizationId
                   AND relationship.student_user_id=:studentUserId
                   AND relationship.status='ACTIVE'
                   AND guardian.status='ACTIVE'
                 ORDER BY relationship.guardian_user_id
                """, Map.of("organizationId", organizationId, "studentUserId", studentUserId), Long.class);
    }
}
