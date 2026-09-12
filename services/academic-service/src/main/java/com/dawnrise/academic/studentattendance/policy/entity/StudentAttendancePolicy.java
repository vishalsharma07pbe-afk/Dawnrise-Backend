package com.dawnrise.academic.studentattendance.policy.entity;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceMode;
import com.dawnrise.academic.studentattendance.policy.enums.LateCountingPeriod;
import com.dawnrise.academic.studentattendance.policy.enums.LatePenaltyOutcome;
import com.dawnrise.academic.studentattendance.policy.exception.InvalidStudentAttendancePolicyException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;

@Entity
@Table(name = "student_attendance_policies")
public class StudentAttendancePolicy {

    @Id
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_mode", nullable = false, length = 30)
    private AttendanceMode attendanceMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "week_start_day", nullable = false, length = 20)
    private DayOfWeek weekStartDay;

    @Column(name = "draft_warning_minutes", nullable = false)
    private Integer draftWarningMinutes;

    @Column(name = "automatic_submission_minutes", nullable = false)
    private Integer automaticSubmissionMinutes;

    @Column(name = "late_penalty_enabled", nullable = false)
    private Boolean latePenaltyEnabled;

    @Column(name = "late_occurrences_threshold", nullable = false)
    private Integer lateOccurrencesThreshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "late_penalty_outcome", nullable = false, length = 20)
    private LatePenaltyOutcome latePenaltyOutcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "late_counting_period", nullable = false, length = 20)
    private LateCountingPeriod lateCountingPeriod;

    @Column(name = "deferred_entry_enabled", nullable = false)
    private Boolean deferredEntryEnabled;

    @Column(name = "teacher_back_entry_days", nullable = false)
    private Integer teacherBackEntryDays;

    @Column(name = "leadership_back_entry_days", nullable = false)
    private Integer leadershipBackEntryDays;

    @Column(name = "automatic_submission_enabled", nullable = false)
    private Boolean automaticSubmissionEnabled;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

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

    protected StudentAttendancePolicy() {
    }

    public StudentAttendancePolicy(
            Long organizationId,
            AttendanceMode attendanceMode,
            DayOfWeek weekStartDay,
            Integer draftWarningMinutes,
            Integer automaticSubmissionMinutes,
            Boolean latePenaltyEnabled,
            Integer lateOccurrencesThreshold,
            LatePenaltyOutcome latePenaltyOutcome,
            LateCountingPeriod lateCountingPeriod,
            Boolean deferredEntryEnabled,
            Integer teacherBackEntryDays,
            Integer leadershipBackEntryDays,
            Boolean automaticSubmissionEnabled,
            Long actorUserId
    ) {
        requirePositive(organizationId, "Organization ID must be positive");
        requirePositive(actorUserId, "Actor user ID must be positive");
        this.organizationId = organizationId;
        this.createdByUserId = actorUserId;
        update(
                attendanceMode,
                weekStartDay,
                draftWarningMinutes,
                automaticSubmissionMinutes,
                latePenaltyEnabled,
                lateOccurrencesThreshold,
                latePenaltyOutcome,
                lateCountingPeriod,
                deferredEntryEnabled,
                teacherBackEntryDays,
                leadershipBackEntryDays,
                automaticSubmissionEnabled,
                actorUserId
        );
    }

    public StudentAttendancePolicy(
            Long organizationId,
            AttendanceMode attendanceMode,
            DayOfWeek weekStartDay,
            Integer draftWarningMinutes,
            Integer automaticSubmissionMinutes,
            Boolean latePenaltyEnabled,
            Integer lateOccurrencesThreshold,
            LatePenaltyOutcome latePenaltyOutcome,
            LateCountingPeriod lateCountingPeriod,
            Long actorUserId
    ) {
        this(
                organizationId,
                attendanceMode,
                weekStartDay,
                draftWarningMinutes,
                automaticSubmissionMinutes,
                latePenaltyEnabled,
                lateOccurrencesThreshold,
                latePenaltyOutcome,
                lateCountingPeriod,
                true,
                0,
                30,
                false,
                actorUserId
        );
    }

    public void update(
            AttendanceMode attendanceMode,
            DayOfWeek weekStartDay,
            Integer draftWarningMinutes,
            Integer automaticSubmissionMinutes,
            Boolean latePenaltyEnabled,
            Integer lateOccurrencesThreshold,
            LatePenaltyOutcome latePenaltyOutcome,
            LateCountingPeriod lateCountingPeriod,
            Boolean deferredEntryEnabled,
            Integer teacherBackEntryDays,
            Integer leadershipBackEntryDays,
            Boolean automaticSubmissionEnabled,
            Long actorUserId
    ) {
        if (attendanceMode != AttendanceMode.DAILY) {
            throw new InvalidStudentAttendancePolicyException(
                    "Attendance mode must be DAILY"
            );
        }
        if (weekStartDay == null) {
            throw new InvalidStudentAttendancePolicyException(
                    "Week start day is required"
            );
        }
        if (draftWarningMinutes == null
                || draftWarningMinutes <= 0
                || draftWarningMinutes > 240) {
            throw new InvalidStudentAttendancePolicyException(
                    "Draft warning minutes must be between 1 and 240"
            );
        }
        if (automaticSubmissionMinutes == null
                || automaticSubmissionMinutes <= draftWarningMinutes
                || automaticSubmissionMinutes > 480) {
            throw new InvalidStudentAttendancePolicyException(
                    "Automatic submission minutes must be greater than warning minutes and no more than 480"
            );
        }
        if (latePenaltyEnabled == null) {
            throw new InvalidStudentAttendancePolicyException(
                    "Late penalty enabled is required"
            );
        }
        if (lateOccurrencesThreshold == null
                || lateOccurrencesThreshold < 1
                || lateOccurrencesThreshold > 100) {
            throw new InvalidStudentAttendancePolicyException(
                    "Late occurrences threshold must be between 1 and 100"
            );
        }
        if (latePenaltyOutcome == null) {
            throw new InvalidStudentAttendancePolicyException(
                    "Late penalty outcome is required"
            );
        }
        if (lateCountingPeriod != LateCountingPeriod.MONTHLY) {
            throw new InvalidStudentAttendancePolicyException(
                    "Late counting period must be MONTHLY"
            );
        }
        if (deferredEntryEnabled == null) {
            throw new InvalidStudentAttendancePolicyException(
                    "Deferred entry enabled is required"
            );
        }
        if (teacherBackEntryDays == null || teacherBackEntryDays < 0 || teacherBackEntryDays > 365) {
            throw new InvalidStudentAttendancePolicyException(
                    "Teacher back entry days must be between 0 and 365"
            );
        }
        if (leadershipBackEntryDays == null || leadershipBackEntryDays < 0 || leadershipBackEntryDays > 365) {
            throw new InvalidStudentAttendancePolicyException(
                    "Leadership back entry days must be between 0 and 365"
            );
        }
        if (automaticSubmissionEnabled == null) {
            throw new InvalidStudentAttendancePolicyException(
                    "Automatic submission enabled is required"
            );
        }
        requirePositive(actorUserId, "Actor user ID must be positive");

        this.attendanceMode = attendanceMode;
        this.weekStartDay = weekStartDay;
        this.draftWarningMinutes = draftWarningMinutes;
        this.automaticSubmissionMinutes = automaticSubmissionMinutes;
        this.latePenaltyEnabled = latePenaltyEnabled;
        this.lateOccurrencesThreshold = lateOccurrencesThreshold;
        this.latePenaltyOutcome = latePenaltyOutcome;
        this.lateCountingPeriod = lateCountingPeriod;
        this.deferredEntryEnabled = deferredEntryEnabled;
        this.teacherBackEntryDays = teacherBackEntryDays;
        this.leadershipBackEntryDays = leadershipBackEntryDays;
        this.automaticSubmissionEnabled = automaticSubmissionEnabled;
        this.updatedByUserId = actorUserId;
    }

    private void requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new InvalidStudentAttendancePolicyException(message);
        }
    }

    public Long getOrganizationId() { return organizationId; }
    public AttendanceMode getAttendanceMode() { return attendanceMode; }
    public DayOfWeek getWeekStartDay() { return weekStartDay; }
    public Integer getDraftWarningMinutes() { return draftWarningMinutes; }
    public Integer getAutomaticSubmissionMinutes() { return automaticSubmissionMinutes; }
    public Boolean getLatePenaltyEnabled() { return latePenaltyEnabled; }
    public Integer getLateOccurrencesThreshold() { return lateOccurrencesThreshold; }
    public LatePenaltyOutcome getLatePenaltyOutcome() { return latePenaltyOutcome; }
    public LateCountingPeriod getLateCountingPeriod() { return lateCountingPeriod; }
    public Boolean getDeferredEntryEnabled() { return deferredEntryEnabled; }
    public Integer getTeacherBackEntryDays() { return teacherBackEntryDays; }
    public Integer getLeadershipBackEntryDays() { return leadershipBackEntryDays; }
    public Boolean getAutomaticSubmissionEnabled() { return automaticSubmissionEnabled; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Long getUpdatedByUserId() { return updatedByUserId; }
    public Long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
