package com.dawnrise.academic.studentattendance.correction.entity;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceRecord;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "student_attendance_correction_items")
public class StudentAttendanceCorrectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;
    @Column(name = "correction_request_id", nullable = false)
    private Long correctionRequestId;
    @Column(name = "attendance_session_id", nullable = false)
    private Long attendanceSessionId;
    @Column(name = "attendance_record_id", nullable = false)
    private Long attendanceRecordId;
    @Column(name = "student_enrollment_id", nullable = false)
    private Long studentEnrollmentId;
    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;
    @Column(name = "expected_attendance_record_version", nullable = false)
    private Long expectedAttendanceRecordVersion;
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_recorded_status", nullable = false, length = 30)
    private AttendanceStatus previousRecordedStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_effective_status", nullable = false, length = 30)
    private AttendanceStatus previousEffectiveStatus;
    @Column(name = "previous_earned_credit", nullable = false, precision = 4, scale = 2)
    private BigDecimal previousEarnedCredit;
    @Column(name = "previous_possible_credit", nullable = false, precision = 4, scale = 2)
    private BigDecimal previousPossibleCredit;
    @Column(name = "previous_late_penalty_applied", nullable = false)
    private boolean previousLatePenaltyApplied;
    @Column(name = "previous_remarks", length = 500)
    private String previousRemarks;
    @Enumerated(EnumType.STRING)
    @Column(name = "proposed_recorded_status", nullable = false, length = 30)
    private AttendanceStatus proposedRecordedStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "proposed_effective_status", nullable = false, length = 30)
    private AttendanceStatus proposedEffectiveStatus;
    @Column(name = "proposed_earned_credit", nullable = false, precision = 4, scale = 2)
    private BigDecimal proposedEarnedCredit;
    @Column(name = "proposed_possible_credit", nullable = false, precision = 4, scale = 2)
    private BigDecimal proposedPossibleCredit;
    @Column(name = "proposed_late_penalty_applied", nullable = false)
    private boolean proposedLatePenaltyApplied;
    @Column(name = "proposed_remarks", length = 500)
    private String proposedRemarks;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected StudentAttendanceCorrectionItem() {
    }

    public StudentAttendanceCorrectionItem(
            Long correctionRequestId,
            StudentAttendanceRecord record,
            Long expectedAttendanceRecordVersion,
            AttendanceStatus proposedRecordedStatus,
            AttendanceStatus proposedEffectiveStatus,
            BigDecimal proposedEarnedCredit,
            BigDecimal proposedPossibleCredit,
            boolean proposedLatePenaltyApplied,
            String proposedRemarks
    ) {
        this.organizationId = record.getOrganizationId();
        this.correctionRequestId = correctionRequestId;
        this.attendanceSessionId = record.getAttendanceSessionId();
        this.attendanceRecordId = record.getId();
        this.studentEnrollmentId = record.getStudentEnrollmentId();
        this.studentUserId = record.getStudentUserId();
        this.expectedAttendanceRecordVersion = expectedAttendanceRecordVersion;
        this.previousRecordedStatus = record.getRecordedStatus();
        this.previousEffectiveStatus = record.getEffectiveStatus();
        this.previousEarnedCredit = record.getEarnedCredit();
        this.previousPossibleCredit = record.getPossibleCredit();
        this.previousLatePenaltyApplied = record.isLatePenaltyApplied();
        this.previousRemarks = record.getRemarks();
        this.proposedRecordedStatus = proposedRecordedStatus;
        this.proposedEffectiveStatus = proposedEffectiveStatus;
        this.proposedEarnedCredit = proposedEarnedCredit;
        this.proposedPossibleCredit = proposedPossibleCredit;
        this.proposedLatePenaltyApplied = proposedLatePenaltyApplied;
        this.proposedRemarks = proposedRemarks == null ? null : proposedRemarks.trim();
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getCorrectionRequestId() { return correctionRequestId; }
    public Long getAttendanceSessionId() { return attendanceSessionId; }
    public Long getAttendanceRecordId() { return attendanceRecordId; }
    public Long getStudentEnrollmentId() { return studentEnrollmentId; }
    public Long getStudentUserId() { return studentUserId; }
    public Long getExpectedAttendanceRecordVersion() { return expectedAttendanceRecordVersion; }
    public AttendanceStatus getPreviousRecordedStatus() { return previousRecordedStatus; }
    public AttendanceStatus getPreviousEffectiveStatus() { return previousEffectiveStatus; }
    public BigDecimal getPreviousEarnedCredit() { return previousEarnedCredit; }
    public BigDecimal getPreviousPossibleCredit() { return previousPossibleCredit; }
    public boolean isPreviousLatePenaltyApplied() { return previousLatePenaltyApplied; }
    public String getPreviousRemarks() { return previousRemarks; }
    public AttendanceStatus getProposedRecordedStatus() { return proposedRecordedStatus; }
    public AttendanceStatus getProposedEffectiveStatus() { return proposedEffectiveStatus; }
    public BigDecimal getProposedEarnedCredit() { return proposedEarnedCredit; }
    public BigDecimal getProposedPossibleCredit() { return proposedPossibleCredit; }
    public boolean isProposedLatePenaltyApplied() { return proposedLatePenaltyApplied; }
    public String getProposedRemarks() { return proposedRemarks; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
