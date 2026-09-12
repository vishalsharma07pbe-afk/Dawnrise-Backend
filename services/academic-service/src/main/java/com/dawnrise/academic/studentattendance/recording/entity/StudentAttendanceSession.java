package com.dawnrise.academic.studentattendance.recording.entity;

import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSessionStatus;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSubmissionType;
import com.dawnrise.academic.studentattendance.recording.exception.InvalidStudentAttendanceRecordingException;
import com.dawnrise.academic.studentattendance.recording.exception.StudentAttendanceRecordingConflictException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "student_attendance_sessions")
public class StudentAttendanceSession {

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
    @Column(name = "academic_calendar_day_id", nullable = false)
    private Long academicCalendarDayId;
    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private StudentAttendanceSessionStatus lifecycleStatus =
            StudentAttendanceSessionStatus.DRAFT;
    @Enumerated(EnumType.STRING)
    @Column(name = "submission_type", length = 20)
    private StudentAttendanceSubmissionType submissionType;
    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;
    @Column(name = "updated_by_user_id", nullable = false)
    private Long updatedByUserId;
    @Column(name = "submitted_by_user_id")
    private Long submittedByUserId;
    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected StudentAttendanceSession() {
    }

    public StudentAttendanceSession(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            Long academicCalendarDayId,
            LocalDate attendanceDate,
            Long actorUserId
    ) {
        requirePositive(organizationId, "Organization ID must be positive");
        requirePositive(academicYearId, "Academic year ID must be positive");
        requirePositive(gradeLevelId, "Grade level ID must be positive");
        requirePositive(sectionId, "Section ID must be positive");
        requirePositive(academicCalendarDayId, "Calendar day ID must be positive");
        requirePositive(actorUserId, "Actor user ID must be positive");
        if (attendanceDate == null) {
            throw new InvalidStudentAttendanceRecordingException("Attendance date is required");
        }
        this.organizationId = organizationId;
        this.academicYearId = academicYearId;
        this.gradeLevelId = gradeLevelId;
        this.sectionId = sectionId;
        this.academicCalendarDayId = academicCalendarDayId;
        this.attendanceDate = attendanceDate;
        this.createdByUserId = actorUserId;
        this.updatedByUserId = actorUserId;
    }

    public void touch(Long actorUserId) {
        requireDraft();
        requirePositive(actorUserId, "Actor user ID must be positive");
        this.updatedByUserId = actorUserId;
    }

    public void submitManually(Long actorUserId) {
        requireDraft();
        requirePositive(actorUserId, "Actor user ID must be positive");
        this.lifecycleStatus = StudentAttendanceSessionStatus.SUBMITTED;
        this.submissionType = StudentAttendanceSubmissionType.MANUAL;
        this.submittedByUserId = actorUserId;
        this.submittedAt = OffsetDateTime.now();
        this.updatedByUserId = actorUserId;
    }

    public void submitAutomatically(OffsetDateTime submittedAt) {
        requireDraft();
        if (submittedAt == null) {
            throw new InvalidStudentAttendanceRecordingException("Submitted timestamp is required");
        }
        this.lifecycleStatus = StudentAttendanceSessionStatus.SUBMITTED;
        this.submissionType = StudentAttendanceSubmissionType.AUTOMATIC;
        this.submittedByUserId = null;
        this.submittedAt = submittedAt;
    }

    public void submitAutomatically() {
        submitAutomatically(OffsetDateTime.now());
    }

    public void requireDraft() {
        if (lifecycleStatus != StudentAttendanceSessionStatus.DRAFT) {
            throw new StudentAttendanceRecordingConflictException(
                    "Submitted attendance cannot be changed"
            );
        }
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
    public Long getAcademicCalendarDayId() { return academicCalendarDayId; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public StudentAttendanceSessionStatus getLifecycleStatus() { return lifecycleStatus; }
    public StudentAttendanceSubmissionType getSubmissionType() { return submissionType; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Long getUpdatedByUserId() { return updatedByUserId; }
    public Long getSubmittedByUserId() { return submittedByUserId; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public Long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
