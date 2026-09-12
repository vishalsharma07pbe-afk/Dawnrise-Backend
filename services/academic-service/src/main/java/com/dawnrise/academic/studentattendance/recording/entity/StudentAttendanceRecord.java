package com.dawnrise.academic.studentattendance.recording.entity;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.recording.exception.InvalidStudentAttendanceRecordingException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "student_attendance_records")
public class StudentAttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
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
    @Column(name = "student_enrollment_id", nullable = false)
    private Long studentEnrollmentId;
    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;
    @Enumerated(EnumType.STRING)
    @Column(name = "recorded_status", nullable = false, length = 30)
    private AttendanceStatus recordedStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "effective_status", nullable = false, length = 30)
    private AttendanceStatus effectiveStatus;
    @Column(name = "earned_credit", nullable = false, precision = 4, scale = 2)
    private BigDecimal earnedCredit;
    @Column(name = "possible_credit", nullable = false, precision = 4, scale = 2)
    private BigDecimal possibleCredit;
    @Column(name = "late_penalty_applied", nullable = false)
    private boolean latePenaltyApplied;
    @Column(name = "remarks", length = 500)
    private String remarks;
    @Column(name = "marked_by_user_id", nullable = false)
    private Long markedByUserId;
    @Column(name = "updated_by_user_id", nullable = false)
    private Long updatedByUserId;
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected StudentAttendanceRecord() {
    }

    public StudentAttendanceRecord(
            StudentAttendanceSession session,
            Long studentEnrollmentId,
            Long studentUserId,
            AttendanceStatus recordedStatus,
            AttendanceStatus effectiveStatus,
            BigDecimal earnedCredit,
            BigDecimal possibleCredit,
            boolean latePenaltyApplied,
            String remarks,
            Long actorUserId
    ) {
        if (session == null) {
            throw new InvalidStudentAttendanceRecordingException("Attendance session is required");
        }
        this.organizationId = session.getOrganizationId();
        this.academicYearId = session.getAcademicYearId();
        this.gradeLevelId = session.getGradeLevelId();
        this.sectionId = session.getSectionId();
        this.attendanceSessionId = session.getId();
        this.studentEnrollmentId = studentEnrollmentId;
        this.studentUserId = studentUserId;
        this.markedByUserId = actorUserId;
        apply(recordedStatus, effectiveStatus, earnedCredit, possibleCredit,
                latePenaltyApplied, remarks, actorUserId);
    }

    public void replace(
            AttendanceStatus recordedStatus,
            AttendanceStatus effectiveStatus,
            BigDecimal earnedCredit,
            BigDecimal possibleCredit,
            boolean latePenaltyApplied,
            String remarks,
            Long actorUserId
    ) {
        apply(recordedStatus, effectiveStatus, earnedCredit, possibleCredit,
                latePenaltyApplied, remarks, actorUserId);
    }

    private void apply(
            AttendanceStatus recordedStatus,
            AttendanceStatus effectiveStatus,
            BigDecimal earnedCredit,
            BigDecimal possibleCredit,
            boolean latePenaltyApplied,
            String remarks,
            Long actorUserId
    ) {
        requirePositive(studentEnrollmentId, "Student enrollment ID must be positive");
        requirePositive(studentUserId, "Student user ID must be positive");
        requirePositive(actorUserId, "Actor user ID must be positive");
        if (recordedStatus == null || effectiveStatus == null) {
            throw new InvalidStudentAttendanceRecordingException("Attendance status is required");
        }
        if (!AttendanceStatus.finalStatuses().contains(recordedStatus)
                || !AttendanceStatus.finalStatuses().contains(effectiveStatus)) {
            throw new InvalidStudentAttendanceRecordingException("Attendance status is not supported");
        }
        if (latePenaltyApplied
                && (recordedStatus != AttendanceStatus.LATE
                || (effectiveStatus != AttendanceStatus.HALF_DAY
                && effectiveStatus != AttendanceStatus.ABSENT))) {
            throw new InvalidStudentAttendanceRecordingException("Late penalty can only convert LATE to HALF_DAY or ABSENT");
        }
        if (!latePenaltyApplied && effectiveStatus != recordedStatus) {
            throw new InvalidStudentAttendanceRecordingException("Effective status must match recorded status without a late penalty");
        }
        validateCredits(earnedCredit, possibleCredit);
        this.recordedStatus = recordedStatus;
        this.effectiveStatus = effectiveStatus;
        this.earnedCredit = earnedCredit;
        this.possibleCredit = possibleCredit;
        this.latePenaltyApplied = latePenaltyApplied;
        this.remarks = normalizeRemarks(remarks);
        this.updatedByUserId = actorUserId;
    }

    private void validateCredits(BigDecimal earnedCredit, BigDecimal possibleCredit) {
        if (earnedCredit == null || possibleCredit == null
                || earnedCredit.compareTo(BigDecimal.ZERO) < 0
                || earnedCredit.compareTo(BigDecimal.ONE) > 0
                || possibleCredit.compareTo(BigDecimal.ZERO) < 0
                || possibleCredit.compareTo(BigDecimal.ONE) > 0
                || earnedCredit.compareTo(possibleCredit) > 0
                || (possibleCredit.compareTo(BigDecimal.ZERO) == 0
                && earnedCredit.compareTo(BigDecimal.ZERO) != 0)) {
            throw new InvalidStudentAttendanceRecordingException("Attendance credits are invalid");
        }
    }

    private String normalizeRemarks(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidStudentAttendanceRecordingException("Remarks cannot be blank");
        }
        if (trimmed.length() > 500) {
            throw new InvalidStudentAttendanceRecordingException("Remarks cannot exceed 500 characters");
        }
        return trimmed;
    }

    private void requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new InvalidStudentAttendanceRecordingException(message);
        }
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getAcademicYearId() { return academicYearId; }
    public Long getGradeLevelId() { return gradeLevelId; }
    public Long getSectionId() { return sectionId; }
    public Long getAttendanceSessionId() { return attendanceSessionId; }
    public Long getStudentEnrollmentId() { return studentEnrollmentId; }
    public Long getStudentUserId() { return studentUserId; }
    public AttendanceStatus getRecordedStatus() { return recordedStatus; }
    public AttendanceStatus getEffectiveStatus() { return effectiveStatus; }
    public BigDecimal getEarnedCredit() { return earnedCredit; }
    public BigDecimal getPossibleCredit() { return possibleCredit; }
    public boolean isLatePenaltyApplied() { return latePenaltyApplied; }
    public String getRemarks() { return remarks; }
    public Long getMarkedByUserId() { return markedByUserId; }
    public Long getUpdatedByUserId() { return updatedByUserId; }
    public Long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
