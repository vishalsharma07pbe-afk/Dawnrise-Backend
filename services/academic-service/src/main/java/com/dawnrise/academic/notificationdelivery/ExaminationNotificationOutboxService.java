package com.dawnrise.academic.notificationdelivery;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ExaminationNotificationOutboxService {
    private final JdbcClient jdbc;
    public ExaminationNotificationOutboxService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public void published(long organizationId, long academicYearId, long examinationId,
                          long actorUserId, String examinationName) {
        jdbc.sql("""
                INSERT INTO examination_notification_outbox
                    (event_id,organization_id,academic_year_id,examination_id,actor_user_id,
                     event_type,idempotency_key,payload,occurred_at)
                VALUES(:event,:org,:year,:exam,:actor,'EXAM_SCHEDULE_PUBLISHED',:key,
                       JSONB_BUILD_OBJECT('examinationName',:name),CURRENT_TIMESTAMP)
                ON CONFLICT(organization_id,idempotency_key) DO NOTHING
                """).param("event", UUID.randomUUID()).param("org", organizationId)
                .param("year", academicYearId).param("exam", examinationId).param("actor", actorUserId)
                .param("key", "exam-schedule-published:" + examinationId).param("name", examinationName).update();
    }
}
