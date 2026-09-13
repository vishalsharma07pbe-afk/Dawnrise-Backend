package com.dawnrise.academic.studentattendance.importing.entity;

import com.dawnrise.academic.studentattendance.importing.enums.StudentAttendanceImportFormat;
import com.dawnrise.academic.studentattendance.importing.exception.InvalidStudentAttendanceImportException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "student_attendance_import_previews")
public class StudentAttendanceImportPreview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;
    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;
    @Column(name = "academic_year_id")
    private Long academicYearId;
    @Column(name = "grade_level_id")
    private Long gradeLevelId;
    @Column(name = "section_id")
    private Long sectionId;
    @Column(name = "attendance_date")
    private LocalDate attendanceDate;
    @Column(name = "preview_fingerprint", nullable = false, length = 71)
    private String previewFingerprint;
    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_format", nullable = false, length = 10)
    private StudentAttendanceImportFormat sourceFormat;
    @Column(name = "row_count", nullable = false)
    private Integer rowCount;
    @Column(name = "can_confirm", nullable = false)
    private Boolean canConfirm;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;
    @Column(name = "confirmed_sync_operation_id")
    private Long confirmedSyncOperationId;
    @Column(name = "confirmed_idempotency_key", length = 128)
    private String confirmedIdempotencyKey;
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected StudentAttendanceImportPreview() {
    }

    public StudentAttendanceImportPreview(
            Long organizationId,
            Long actorUserId,
            Long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            LocalDate attendanceDate,
            String previewFingerprint,
            String originalFileName,
            StudentAttendanceImportFormat sourceFormat,
            Integer rowCount,
            boolean canConfirm,
            String payloadJson,
            OffsetDateTime expiresAt
    ) {
        if (organizationId == null || organizationId <= 0
                || actorUserId == null || actorUserId <= 0
                || previewFingerprint == null
                || !previewFingerprint.matches("^sha256:[0-9a-f]{64}$")
                || originalFileName == null || originalFileName.isBlank()
                || sourceFormat == null
                || rowCount == null || rowCount < 1 || rowCount > 100
                || payloadJson == null || payloadJson.isBlank()
                || expiresAt == null) {
            throw new InvalidStudentAttendanceImportException(
                    "Attendance import preview is incomplete"
            );
        }
        if (canConfirm && (academicYearId == null || gradeLevelId == null
                || sectionId == null || attendanceDate == null)) {
            throw new InvalidStudentAttendanceImportException(
                    "Confirmable attendance import context is incomplete"
            );
        }
        this.organizationId = organizationId;
        this.actorUserId = actorUserId;
        this.academicYearId = academicYearId;
        this.gradeLevelId = gradeLevelId;
        this.sectionId = sectionId;
        this.attendanceDate = attendanceDate;
        this.previewFingerprint = previewFingerprint;
        this.originalFileName = originalFileName.trim();
        this.sourceFormat = sourceFormat;
        this.rowCount = rowCount;
        this.canConfirm = canConfirm;
        this.payloadJson = payloadJson;
        this.expiresAt = expiresAt;
    }

    public void markConfirmed(Long operationId, String idempotencyKey) {
        if (!Boolean.TRUE.equals(canConfirm) || operationId == null || operationId <= 0
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidStudentAttendanceImportException(
                    "Attendance import preview cannot be confirmed"
            );
        }
        String normalizedKey = idempotencyKey.trim();
        if (confirmedSyncOperationId != null
                && (!confirmedSyncOperationId.equals(operationId)
                || !confirmedIdempotencyKey.equals(normalizedKey))) {
            throw new InvalidStudentAttendanceImportException(
                    "Attendance import preview was already confirmed"
            );
        }
        this.confirmedSyncOperationId = operationId;
        this.confirmedIdempotencyKey = normalizedKey;
    }

    public void reserveConfirmation(String idempotencyKey) {
        if (!Boolean.TRUE.equals(canConfirm)
                || idempotencyKey == null
                || idempotencyKey.isBlank()) {
            throw new InvalidStudentAttendanceImportException(
                    "Attendance import preview cannot be confirmed"
            );
        }
        String normalized = idempotencyKey.trim();
        if (confirmedIdempotencyKey != null
                && !confirmedIdempotencyKey.equals(normalized)) {
            throw new InvalidStudentAttendanceImportException(
                    "Attendance import preview was reserved with another idempotency key"
            );
        }
        this.confirmedIdempotencyKey = normalized;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getActorUserId() { return actorUserId; }
    public Long getAcademicYearId() { return academicYearId; }
    public Long getGradeLevelId() { return gradeLevelId; }
    public Long getSectionId() { return sectionId; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public String getPreviewFingerprint() { return previewFingerprint; }
    public String getOriginalFileName() { return originalFileName; }
    public StudentAttendanceImportFormat getSourceFormat() { return sourceFormat; }
    public Integer getRowCount() { return rowCount; }
    public Boolean getCanConfirm() { return canConfirm; }
    public String getPayloadJson() { return payloadJson; }
    public Long getConfirmedSyncOperationId() { return confirmedSyncOperationId; }
    public String getConfirmedIdempotencyKey() { return confirmedIdempotencyKey; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public Long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
