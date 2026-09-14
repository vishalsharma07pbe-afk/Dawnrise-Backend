package com.dawnrise.academic.studentattendance.notificationoutbox.entity;

import com.dawnrise.academic.studentattendance.notificationoutbox.enums.StudentAttendanceNotificationEventSource;
import com.dawnrise.academic.studentattendance.notificationoutbox.enums.StudentAttendanceNotificationEventType;
import com.dawnrise.academic.studentattendance.notificationoutbox.enums.StudentAttendanceNotificationPublicationStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "student_attendance_notification_outbox",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_student_attendance_notification_outbox_event",
                        columnNames = "event_id"
                ),
                @UniqueConstraint(
                        name = "uk_student_attendance_notification_outbox_idempotency",
                        columnNames = {
                                "organization_id",
                                "idempotency_key"
                        }
                )
        }
)
public class StudentAttendanceNotificationOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(name = "grade_level_id", nullable = false)
    private Long gradeLevelId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "attendance_session_id", nullable = false)
    private Long attendanceSessionId;

    @Column(name = "attendance_record_id", nullable = false)
    private Long attendanceRecordId;

    @Column(name = "correction_request_id")
    private Long correctionRequestId;

    @Column(name = "student_enrollment_id", nullable = false)
    private Long studentEnrollmentId;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private StudentAttendanceNotificationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_source", nullable = false, length = 40)
    private StudentAttendanceNotificationEventSource eventSource;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false, length = 20)
    private StudentAttendanceNotificationPublicationStatus
            publicationStatus;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "available_at", nullable = false)
    private OffsetDateTime availableAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected StudentAttendanceNotificationOutboxEvent() {
    }

    public StudentAttendanceNotificationOutboxEvent(
            UUID eventId,
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            Long attendanceSessionId,
            Long attendanceRecordId,
            Long correctionRequestId,
            Long studentEnrollmentId,
            Long studentUserId,
            LocalDate attendanceDate,
            StudentAttendanceNotificationEventType eventType,
            StudentAttendanceNotificationEventSource eventSource,
            String idempotencyKey,
            String payload,
            OffsetDateTime occurredAt
    ) {
        this.eventId = requireEventId(eventId);
        this.organizationId =
                requirePositive(organizationId, "Organization ID");
        this.academicYearId =
                requirePositive(academicYearId, "Academic year ID");
        this.gradeLevelId =
                requirePositive(gradeLevelId, "Grade level ID");
        this.sectionId =
                requirePositive(sectionId, "Section ID");
        this.attendanceSessionId =
                requirePositive(
                        attendanceSessionId,
                        "Attendance session ID"
                );
        this.attendanceRecordId =
                requirePositive(
                        attendanceRecordId,
                        "Attendance record ID"
                );
        this.studentEnrollmentId =
                requirePositive(
                        studentEnrollmentId,
                        "Student enrollment ID"
                );
        this.studentUserId =
                requirePositive(studentUserId, "Student user ID");

        if (eventSource
                == StudentAttendanceNotificationEventSource
                .CORRECTION_APPROVAL) {
            this.correctionRequestId =
                    requirePositive(
                            correctionRequestId,
                            "Correction request ID"
                    );
        } else {
            if (correctionRequestId != null) {
                throw new IllegalArgumentException(
                        "Correction request ID is only valid "
                                + "for correction events"
                );
            }
            this.correctionRequestId = null;
        }

        if (attendanceDate == null) {
            throw new IllegalArgumentException(
                    "Attendance date is required"
            );
        }
        if (eventType == null) {
            throw new IllegalArgumentException(
                    "Event type is required"
            );
        }
        if (eventSource == null) {
            throw new IllegalArgumentException(
                    "Event source is required"
            );
        }

        this.attendanceDate = attendanceDate;
        this.eventType = eventType;
        this.eventSource = eventSource;
        this.idempotencyKey = requireText(
                idempotencyKey,
                200,
                "Idempotency key"
        );
        this.payload = requireText(
                payload,
                Integer.MAX_VALUE,
                "Payload"
        );
        this.publicationStatus =
                StudentAttendanceNotificationPublicationStatus.PENDING;
        this.attemptCount = 0;
        this.occurredAt = requireTimestamp(occurredAt);
        this.availableAt = occurredAt;
    }

    private static UUID requireEventId(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException(
                    "Event ID is required"
            );
        }
        return eventId;
    }

    private static Long requirePositive(
            Long value,
            String fieldName
    ) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }
        return value;
    }

    private static String requireText(
            String value,
            int maximumLength,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required"
            );
        }

        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " is too long"
            );
        }
        return normalized;
    }

    private static OffsetDateTime requireTimestamp(
            OffsetDateTime value
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Occurred timestamp is required"
            );
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getAcademicYearId() {
        return academicYearId;
    }

    public Long getGradeLevelId() {
        return gradeLevelId;
    }

    public Long getSectionId() {
        return sectionId;
    }

    public Long getAttendanceSessionId() {
        return attendanceSessionId;
    }

    public Long getAttendanceRecordId() {
        return attendanceRecordId;
    }

    public Long getCorrectionRequestId() {
        return correctionRequestId;
    }

    public Long getStudentEnrollmentId() {
        return studentEnrollmentId;
    }

    public Long getStudentUserId() {
        return studentUserId;
    }

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public StudentAttendanceNotificationEventType getEventType() {
        return eventType;
    }

    public StudentAttendanceNotificationEventSource getEventSource() {
        return eventSource;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getPayload() {
        return payload;
    }

    public StudentAttendanceNotificationPublicationStatus
    getPublicationStatus() {
        return publicationStatus;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public OffsetDateTime getAvailableAt() {
        return availableAt;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public Long getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
