package com.dawnrise.academic.studentattendance.policy.entity;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.policy.exception.InvalidStudentAttendancePolicyException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "student_attendance_status_policies")
@IdClass(StudentAttendanceStatusPolicyId.class)
public class StudentAttendanceStatusPolicy {

    @Id
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false, length = 30)
    private AttendanceStatus attendanceStatus;

    @Column(name = "earned_credit", nullable = false, precision = 4, scale = 2)
    private BigDecimal earnedCredit;

    @Column(name = "possible_credit", nullable = false, precision = 4, scale = 2)
    private BigDecimal possibleCredit;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected StudentAttendanceStatusPolicy() {
    }

    public StudentAttendanceStatusPolicy(
            Long organizationId,
            AttendanceStatus attendanceStatus,
            BigDecimal earnedCredit,
            BigDecimal possibleCredit
    ) {
        if (organizationId == null || organizationId <= 0) {
            throw new InvalidStudentAttendancePolicyException(
                    "Organization ID must be positive"
            );
        }
        this.organizationId = organizationId;
        this.attendanceStatus = attendanceStatus;
        updateCredits(earnedCredit, possibleCredit);
    }

    public void updateCredits(
            BigDecimal earnedCredit,
            BigDecimal possibleCredit
    ) {
        if (!AttendanceStatus.finalStatuses().contains(attendanceStatus)) {
            throw new InvalidStudentAttendancePolicyException(
                    "Attendance status policy must use a final attendance status"
            );
        }
        if (earnedCredit == null || possibleCredit == null) {
            throw new InvalidStudentAttendancePolicyException(
                    "Attendance credits are required"
            );
        }
        if (earnedCredit.compareTo(BigDecimal.ZERO) < 0
                || earnedCredit.compareTo(BigDecimal.ONE) > 0
                || possibleCredit.compareTo(BigDecimal.ZERO) < 0
                || possibleCredit.compareTo(BigDecimal.ONE) > 0
                || earnedCredit.compareTo(possibleCredit) > 0
                || (possibleCredit.compareTo(BigDecimal.ZERO) == 0
                && earnedCredit.compareTo(BigDecimal.ZERO) != 0)) {
            throw new InvalidStudentAttendancePolicyException(
                    "Attendance credits must be between 0.00 and 1.00 with earned credit no greater than possible credit"
            );
        }
        this.earnedCredit = earnedCredit;
        this.possibleCredit = possibleCredit;
    }

    public Long getOrganizationId() { return organizationId; }
    public AttendanceStatus getAttendanceStatus() { return attendanceStatus; }
    public BigDecimal getEarnedCredit() { return earnedCredit; }
    public BigDecimal getPossibleCredit() { return possibleCredit; }
    public Long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
