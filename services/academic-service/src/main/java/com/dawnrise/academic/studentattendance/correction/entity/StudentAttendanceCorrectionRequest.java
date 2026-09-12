package com.dawnrise.academic.studentattendance.correction.entity;

import com.dawnrise.academic.studentattendance.correction.enums.StudentAttendanceCorrectionStatus;
import com.dawnrise.academic.studentattendance.correction.exception.InvalidStudentAttendanceCorrectionException;
import com.dawnrise.academic.studentattendance.correction.exception.StudentAttendanceCorrectionConflictException;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceSession;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "student_attendance_correction_requests")
public class StudentAttendanceCorrectionRequest {

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
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StudentAttendanceCorrectionStatus status;
    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;
    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;
    @Column(name = "review_comment", length = 500)
    private String reviewComment;
    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;
    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected StudentAttendanceCorrectionRequest() {
    }

    public StudentAttendanceCorrectionRequest(
            StudentAttendanceSession session,
            String reason,
            Long requestedByUserId,
            OffsetDateTime requestedAt
    ) {
        if (session == null) {
            throw new InvalidStudentAttendanceCorrectionException("Attendance session is required");
        }
        requirePositive(requestedByUserId, "Requested by user ID must be positive");
        if (requestedAt == null) {
            throw new InvalidStudentAttendanceCorrectionException("Requested timestamp is required");
        }
        this.organizationId = session.getOrganizationId();
        this.academicYearId = session.getAcademicYearId();
        this.gradeLevelId = session.getGradeLevelId();
        this.sectionId = session.getSectionId();
        this.attendanceSessionId = session.getId();
        this.reason = normalize(reason, true, "Reason");
        this.status = StudentAttendanceCorrectionStatus.PENDING;
        this.requestedByUserId = requestedByUserId;
        this.requestedAt = requestedAt;
    }

    public void cancel(Long actorUserId, Long expectedVersion, String comment, OffsetDateTime now) {
        requireRequester(actorUserId);
        requireExpectedVersion(expectedVersion);
        requirePending();
        requireTimestamp(now);
        this.status = StudentAttendanceCorrectionStatus.CANCELLED;
        this.reviewComment = normalize(comment, false, "Review comment");
        this.reviewedAt = now;
    }

    public void reject(Long reviewerUserId, Long expectedVersion, String comment, OffsetDateTime now) {
        requireReviewer(reviewerUserId);
        requireExpectedVersion(expectedVersion);
        requirePending();
        if (reviewerUserId.equals(requestedByUserId)) {
            throw new StudentAttendanceCorrectionConflictException("Requester cannot review their own correction");
        }
        requireTimestamp(now);
        this.status = StudentAttendanceCorrectionStatus.REJECTED;
        this.reviewedByUserId = reviewerUserId;
        this.reviewComment = normalize(comment, true, "Review comment");
        this.reviewedAt = now;
    }

    public void approve(Long reviewerUserId, Long expectedVersion, String comment, OffsetDateTime now) {
        requireReviewer(reviewerUserId);
        requireExpectedVersion(expectedVersion);
        requirePending();
        if (reviewerUserId.equals(requestedByUserId)) {
            throw new StudentAttendanceCorrectionConflictException("Requester cannot review their own correction");
        }
        requireTimestamp(now);
        this.status = StudentAttendanceCorrectionStatus.APPROVED;
        this.reviewedByUserId = reviewerUserId;
        this.reviewComment = normalize(comment, false, "Review comment");
        this.reviewedAt = now;
    }

    public void requirePending() {
        if (status != StudentAttendanceCorrectionStatus.PENDING) {
            throw new StudentAttendanceCorrectionConflictException("Correction request is not pending");
        }
    }

    private void requireRequester(Long actorUserId) {
        requirePositive(actorUserId, "Actor user ID must be positive");
        if (!actorUserId.equals(requestedByUserId)) {
            throw new StudentAttendanceCorrectionConflictException("Only the requester can cancel this correction");
        }
    }

    private void requireReviewer(Long reviewerUserId) {
        requirePositive(reviewerUserId, "Reviewer user ID must be positive");
    }

    private void requireExpectedVersion(Long expectedVersion) {
        if (expectedVersion == null || version == null || !version.equals(expectedVersion)) {
            throw new StudentAttendanceCorrectionConflictException("Correction request version is stale");
        }
    }

    private void requireTimestamp(OffsetDateTime now) {
        if (now == null) {
            throw new InvalidStudentAttendanceCorrectionException("Review timestamp is required");
        }
    }

    private String normalize(String value, boolean required, String label) {
        if (value == null) {
            if (required) {
                throw new InvalidStudentAttendanceCorrectionException(label + " is required");
            }
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            if (required) {
                throw new InvalidStudentAttendanceCorrectionException(label + " is required");
            }
            return null;
        }
        if (trimmed.length() > 500) {
            throw new InvalidStudentAttendanceCorrectionException(label + " cannot exceed 500 characters");
        }
        return trimmed;
    }

    private void requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new InvalidStudentAttendanceCorrectionException(message);
        }
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getAcademicYearId() { return academicYearId; }
    public Long getGradeLevelId() { return gradeLevelId; }
    public Long getSectionId() { return sectionId; }
    public Long getAttendanceSessionId() { return attendanceSessionId; }
    public String getReason() { return reason; }
    public StudentAttendanceCorrectionStatus getStatus() { return status; }
    public Long getRequestedByUserId() { return requestedByUserId; }
    public Long getReviewedByUserId() { return reviewedByUserId; }
    public String getReviewComment() { return reviewComment; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public Long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
