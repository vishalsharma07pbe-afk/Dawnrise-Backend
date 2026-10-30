package com.dawnrise.academic.notificationdelivery;

import com.dawnrise.academic.reportcard.entity.ReportCard;
import com.dawnrise.academic.results.entity.ResultPublication;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AcademicPublicationNotificationOutboxService {
    private final JdbcClient jdbc;

    public AcademicPublicationNotificationOutboxService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void resultPublished(ResultPublication publication) {
        List<Long> students = jdbc.sql("""
                SELECT DISTINCT m.student_user_id
                  FROM result_subject_marks m
                  JOIN result_subject_sheets s ON s.id=m.subject_sheet_id
                   AND s.organization_id=m.organization_id AND s.academic_year_id=m.academic_year_id
                   AND s.grade_level_id=m.grade_level_id AND s.section_id=m.section_id
                 WHERE s.organization_id=:org AND s.academic_year_id=:year
                   AND s.examination_id=:exam AND s.grade_level_id=:grade AND s.section_id=:section
                   AND s.status='APPROVED'
                 ORDER BY m.student_user_id
                """).param("org", publication.getOrganizationId())
                .param("year", publication.getAcademicYearId()).param("exam", publication.getExaminationId())
                .param("grade", publication.getGradeLevelId()).param("section", publication.getSectionId())
                .query(Long.class).list();
        for (Long student : students) {
            insert(publication.getOrganizationId(), publication.getAcademicYearId(), student,
                    publication.getId(), "RESULT_PUBLISHED",
                    "result-published:" + publication.getId() + ":student:" + student,
                    "{\"publicationId\":" + publication.getId() + "}", publication.getPublishedAt());
        }
    }

    public void reportCardPublished(ReportCard card) {
        insert(card.getOrganizationId(), card.getAcademicYearId(), card.getStudentUserId(), card.getId(),
                "REPORT_CARD_PUBLISHED", "report-card-published:" + card.getId(),
                "{\"reportCardId\":" + card.getId() + "}", card.getPublishedAt());
    }

    private void insert(long organizationId, long academicYearId, long studentUserId,
                        long artifactId, String eventType, String idempotencyKey,
                        String payload, java.time.OffsetDateTime occurredAt) {
        jdbc.sql("""
                INSERT INTO academic_publication_notification_outbox
                    (event_id,organization_id,academic_year_id,student_user_id,artifact_id,event_type,
                     idempotency_key,payload,occurred_at)
                VALUES(:event,:org,:year,:student,:artifact,:type,:key,CAST(:payload AS JSONB),:occurred)
                ON CONFLICT(organization_id,idempotency_key) DO NOTHING
                """).param("event", UUID.randomUUID()).param("org", organizationId)
                .param("year", academicYearId).param("student", studentUserId)
                .param("artifact", artifactId).param("type", eventType).param("key", idempotencyKey)
                .param("payload", payload).param("occurred", occurredAt).update();
    }
}
