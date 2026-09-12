package com.dawnrise.academic.studentattendance.policy.entity;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceMode;
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
                actorUserId
        );
    }

    public void update(
            AttendanceMode attendanceMode,
            DayOfWeek weekStartDay,
            Integer draftWarningMinutes,
            Integer automaticSubmissionMinutes,
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
        requirePositive(actorUserId, "Actor user ID must be positive");

        this.attendanceMode = attendanceMode;
        this.weekStartDay = weekStartDay;
        this.draftWarningMinutes = draftWarningMinutes;
        this.automaticSubmissionMinutes = automaticSubmissionMinutes;
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
    public Long getCreatedByUserId() { return createdByUserId; }
    public Long getUpdatedByUserId() { return updatedByUserId; }
    public Long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
