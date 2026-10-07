package com.dawnrise.academic.results.entity;

import com.dawnrise.academic.results.exception.InvalidResultException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "result_subject_marks")
public class ResultSubjectMark {
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

    @Column(name = "subject_sheet_id", nullable = false)
    private Long subjectSheetId;

    @Column(name = "student_enrollment_id", nullable = false)
    private Long studentEnrollmentId;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "roll_number_snapshot", nullable = false, length = 30)
    private String rollNumberSnapshot;

    @Column(name = "absent", nullable = false)
    private Boolean absent = false;

    @Column(name = "marks_obtained", precision = 7, scale = 2)
    private BigDecimal marksObtained;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ResultSubjectMark() {
    }

    public ResultSubjectMark(long organizationId, long academicYearId,
                             long gradeLevelId, long sectionId,
                             long subjectSheetId, long studentEnrollmentId,
                             long studentUserId, String rollNumberSnapshot) {
        if (organizationId <= 0 || academicYearId <= 0 || gradeLevelId <= 0
                || sectionId <= 0 || subjectSheetId <= 0
                || studentEnrollmentId <= 0 || studentUserId <= 0) {
            throw new InvalidResultException("Result mark IDs must be positive");
        }
        if (rollNumberSnapshot == null || rollNumberSnapshot.isBlank()) {
            throw new InvalidResultException("Roll number snapshot is required");
        }
        String normalizedRoll = rollNumberSnapshot.trim();
        if (normalizedRoll.length() > 30) {
            throw new InvalidResultException("Roll number snapshot cannot exceed 30 characters");
        }
        this.organizationId = organizationId;
        this.academicYearId = academicYearId;
        this.gradeLevelId = gradeLevelId;
        this.sectionId = sectionId;
        this.subjectSheetId = subjectSheetId;
        this.studentEnrollmentId = studentEnrollmentId;
        this.studentUserId = studentUserId;
        this.rollNumberSnapshot = normalizedRoll;
    }

    public void record(boolean absent, BigDecimal marksObtained) {
        if (absent) {
            if (marksObtained != null) {
                throw new InvalidResultException("Absent students cannot have marks");
            }
            this.absent = true;
            this.marksObtained = null;
            return;
        }
        if (marksObtained != null && (marksObtained.signum() < 0
                || marksObtained.scale() > 2)) {
            throw new InvalidResultException("Marks must be non-negative with at most two decimals");
        }
        this.absent = false;
        this.marksObtained = marksObtained;
    }

    public boolean isComplete() {
        return Boolean.TRUE.equals(absent) || marksObtained != null;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getAcademicYearId() { return academicYearId; }
    public Long getGradeLevelId() { return gradeLevelId; }
    public Long getSectionId() { return sectionId; }
    public Long getSubjectSheetId() { return subjectSheetId; }
    public Long getStudentEnrollmentId() { return studentEnrollmentId; }
    public Long getStudentUserId() { return studentUserId; }
    public String getRollNumberSnapshot() { return rollNumberSnapshot; }
    public Boolean getAbsent() { return absent; }
    public BigDecimal getMarksObtained() { return marksObtained; }
    public Long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
