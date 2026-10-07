package com.dawnrise.academic.examination.entity;

import com.dawnrise.academic.examination.exception.InvalidExaminationException;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "scheduled_assessments")
public class ScheduledAssessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(name = "examination_id", nullable = false)
    private Long examinationId;

    @Column(name = "grade_level_id", nullable = false)
    private Long gradeLevelId;

    @Column(name = "grade_level_subject_id", nullable = false)
    private Long gradeLevelSubjectId;

    @Column(name = "assessment_date", nullable = false)
    private LocalDate assessmentDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "maximum_marks", nullable = false, precision = 7, scale = 2)
    private BigDecimal maximumMarks;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ScheduledAssessment() {}

    public ScheduledAssessment(long organizationId, long academicYearId,
                               long examinationId, long gradeLevelId,
                               long gradeLevelSubjectId, LocalDate assessmentDate,
                               LocalTime startTime, LocalTime endTime,
                               BigDecimal maximumMarks) {
        if (organizationId <= 0 || academicYearId <= 0 || examinationId <= 0
                || gradeLevelId <= 0 || gradeLevelSubjectId <= 0) {
            throw new InvalidExaminationException("Assessment IDs must be positive");
        }
        this.organizationId = organizationId;
        this.academicYearId = academicYearId;
        this.examinationId = examinationId;
        this.gradeLevelId = gradeLevelId;
        this.gradeLevelSubjectId = gradeLevelSubjectId;
        reschedule(assessmentDate, startTime, endTime, maximumMarks);
    }

    public void reschedule(LocalDate assessmentDate, LocalTime startTime,
                           LocalTime endTime, BigDecimal maximumMarks) {
        if (assessmentDate == null || startTime == null || endTime == null
                || maximumMarks == null) {
            throw new InvalidExaminationException("Date, times and maximum marks are required");
        }
        if (!startTime.isBefore(endTime)) {
            throw new InvalidExaminationException("Start time must be before end time");
        }
        if (maximumMarks.signum() <= 0 || maximumMarks.scale() > 2
                || maximumMarks.compareTo(new BigDecimal("99999.99")) > 0) {
            throw new InvalidExaminationException("Maximum marks must be between 0.01 and 99999.99 with at most two decimals");
        }
        this.assessmentDate = assessmentDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maximumMarks = maximumMarks;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getAcademicYearId() { return academicYearId; }
    public Long getExaminationId() { return examinationId; }
    public Long getGradeLevelId() { return gradeLevelId; }
    public Long getGradeLevelSubjectId() { return gradeLevelSubjectId; }
    public LocalDate getAssessmentDate() { return assessmentDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public BigDecimal getMaximumMarks() { return maximumMarks; }
    public Long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
