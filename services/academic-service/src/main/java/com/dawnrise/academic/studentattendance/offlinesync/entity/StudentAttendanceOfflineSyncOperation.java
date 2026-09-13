package com.dawnrise.academic.studentattendance.offlinesync.entity;

import com.dawnrise.academic.studentattendance.offlinesync.enums.StudentAttendanceOfflineSyncFailureCategory;
import com.dawnrise.academic.studentattendance.offlinesync.enums.StudentAttendanceOfflineSyncMode;
import com.dawnrise.academic.studentattendance.offlinesync.enums.StudentAttendanceOfflineSyncStatus;
import com.dawnrise.academic.studentattendance.offlinesync.exception.InvalidStudentAttendanceOfflineSyncException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "student_attendance_offline_sync_operations")
public class StudentAttendanceOfflineSyncOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;
    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;
    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;
    @Column(name = "grade_level_id", nullable = false)
    private Long gradeLevelId;
    @Column(name = "section_id", nullable = false)
    private Long sectionId;
    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 71)
    private String requestHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_mode", nullable = false, length = 20)
    private StudentAttendanceOfflineSyncMode syncMode;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StudentAttendanceOfflineSyncStatus status;
    @Column(name = "attendance_session_id")
    private Long attendanceSessionId;
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category", length = 30)
    private StudentAttendanceOfflineSyncFailureCategory failureCategory;
    @Column(name = "failure_message", length = 500)
    private String failureMessage;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private String resultJson;
    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected StudentAttendanceOfflineSyncOperation() {
    }

    public StudentAttendanceOfflineSyncOperation(
            Long organizationId,
            Long actorUserId,
            Long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            LocalDate attendanceDate,
            String idempotencyKey,
            String requestHash,
            StudentAttendanceOfflineSyncMode syncMode
    ) {
        requirePositive(organizationId, "Organization ID must be positive");
        requirePositive(actorUserId, "Actor user ID must be positive");
        requirePositive(academicYearId, "Academic year ID must be positive");
        requirePositive(gradeLevelId, "Grade level ID must be positive");
        requirePositive(sectionId, "Section ID must be positive");
        if (attendanceDate == null || syncMode == null) {
            throw new InvalidStudentAttendanceOfflineSyncException(
                    "Attendance date and sync mode are required"
            );
        }
        this.idempotencyKey = normalizeRequired(idempotencyKey, "Idempotency key");
        if (this.idempotencyKey.length() > 128) {
            throw new InvalidStudentAttendanceOfflineSyncException(
                    "Idempotency-Key header cannot exceed 128 characters"
            );
        }
        if (requestHash == null || !requestHash.matches("^sha256:[0-9a-f]{64}$")) {
            throw new InvalidStudentAttendanceOfflineSyncException("Request hash is invalid");
        }
        this.organizationId = organizationId;
        this.actorUserId = actorUserId;
        this.academicYearId = academicYearId;
        this.gradeLevelId = gradeLevelId;
        this.sectionId = sectionId;
        this.attendanceDate = attendanceDate;
        this.requestHash = requestHash;
        this.syncMode = syncMode;
        this.status = StudentAttendanceOfflineSyncStatus.RUNNING;
        this.startedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void succeed(Long sessionId, String resultJson) {
        requireRunning();
        requirePositive(sessionId, "Attendance session ID must be positive");
        if (resultJson == null || resultJson.isBlank()) {
            throw new InvalidStudentAttendanceOfflineSyncException("Sync result is required");
        }
        this.status = StudentAttendanceOfflineSyncStatus.SUCCEEDED;
        this.attendanceSessionId = sessionId;
        this.resultJson = resultJson;
        this.completedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void restartAfterInterruption() {
        requireRunning();
        this.startedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void fail(StudentAttendanceOfflineSyncFailureCategory category, String message) {
        requireRunning();
        if (category == null) {
            throw new InvalidStudentAttendanceOfflineSyncException("Failure category is required");
        }
        String normalized = normalizeRequired(message, "Failure message");
        this.status = StudentAttendanceOfflineSyncStatus.FAILED;
        this.failureCategory = category;
        this.failureMessage = normalized.length() > 500
                ? normalized.substring(0, 500)
                : normalized;
        this.completedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    private void requireRunning() {
        if (status != StudentAttendanceOfflineSyncStatus.RUNNING) {
            throw new InvalidStudentAttendanceOfflineSyncException("Sync operation is already terminal");
        }
    }

    private static void requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new InvalidStudentAttendanceOfflineSyncException(message);
        }
    }

    private static String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidStudentAttendanceOfflineSyncException(field + " is required");
        }
        return value.trim();
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getActorUserId() { return actorUserId; }
    public Long getAcademicYearId() { return academicYearId; }
    public Long getGradeLevelId() { return gradeLevelId; }
    public Long getSectionId() { return sectionId; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public StudentAttendanceOfflineSyncMode getSyncMode() { return syncMode; }
    public StudentAttendanceOfflineSyncStatus getStatus() { return status; }
    public Long getAttendanceSessionId() { return attendanceSessionId; }
    public StudentAttendanceOfflineSyncFailureCategory getFailureCategory() { return failureCategory; }
    public String getFailureMessage() { return failureMessage; }
    public String getResultJson() { return resultJson; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public Long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
