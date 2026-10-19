package com.dawnrise.academic.reportcard.entity;

import com.dawnrise.academic.reportcard.enums.ReportCardStatus;
import com.dawnrise.academic.results.exception.*;
import jakarta.persistence.*;
import org.hibernate.annotations.*;

import java.math.*;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "report_cards")
public class ReportCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "organization_id")
    private Long organizationId;
    @Column(name = "publication_id")
    private Long publicationId;
    @Column(name = "academic_year_id")
    private Long academicYearId;
    @Column(name = "examination_id")
    private Long examinationId;
    @Column(name = "grade_level_id")
    private Long gradeLevelId;
    @Column(name = "section_id")
    private Long sectionId;
    @Column(name = "student_user_id")
    private Long studentUserId;
    @Column(name = "enrollment_id")
    private Long enrollmentId;
    @Column(name = "grading_policy_id")
    private Long gradingPolicyId;
    @Enumerated(EnumType.STRING)
    private ReportCardStatus status = ReportCardStatus.DRAFT;
    @Column(name = "academic_year_name")
    private String academicYearName;
    @Column(name = "examination_name")
    private String examinationName;
    @Column(name = "grade_name")
    private String gradeName;
    @Column(name = "section_name")
    private String sectionName;
    @Column(name = "roll_number")
    private String rollNumber;
    @Column(name = "result_published_at")
    private OffsetDateTime resultPublishedAt;
    @Column(name = "policy_name")
    private String policyName;
    @Column(name = "policy_version")
    private Integer policyVersion;
    @Column(name = "subject_pass_percentage", precision = 9, scale = 6)
    private BigDecimal subjectPassPercentage;
    @Column(name = "overall_pass_percentage", precision = 9, scale = 6)
    private BigDecimal overallPassPercentage;
    @Column(name = "absent_counts_as_failure")
    private Boolean absentCountsAsFailure;
    @Column(name = "require_every_subject_to_pass")
    private Boolean requireEverySubjectToPass;
    @Column(name = "decimal_scale")
    private Integer decimalScale;
    @Enumerated(EnumType.STRING)
    @Column(name = "rounding_mode")
    private RoundingMode roundingMode;
    @Column(name = "total_maximum_marks")
    private BigDecimal totalMaximumMarks;
    @Column(name = "total_obtained_marks")
    private BigDecimal totalObtainedMarks;
    @Column(name = "aggregate_percentage", precision = 9, scale = 6)
    private BigDecimal aggregatePercentage;
    @Column(name = "overall_grade")
    private String overallGrade;
    private Boolean passed;
    @Column(name = "absent_subject_count")
    private Integer absentSubjectCount;
    private Boolean partial;
    @Column(name = "return_reason")
    private String returnReason;
    @Version
    private Long version;
    @Column(name = "created_by_user_id")
    private Long createdByUserId;
    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;
    @Column(name = "published_by_user_id")
    private Long publishedByUserId;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
    @OneToMany(cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "report_card_id", nullable = false)
    @OrderBy("displayOrder ASC")
    private List<ReportCardSubject> subjects = new ArrayList<>();

    protected ReportCard() {
    }

    public ReportCard(long o, long publication, long year, long exam, long grade, long section, long student, long enrollment, long policy, String yearName, String examName, String gradeName, String sectionName, String roll, OffsetDateTime resultPublished, String policyName, int policyVersion, BigDecimal subjectPass, BigDecimal overallPass, boolean absentFails, boolean every, int scale, RoundingMode rounding, List<ReportCardSubject> rows, BigDecimal totalMax, BigDecimal totalObtained, BigDecimal aggregate, String overallGrade, boolean passed, int absentCount, boolean partial, long actor) {
        organizationId = o;
        publicationId = publication;
        academicYearId = year;
        examinationId = exam;
        gradeLevelId = grade;
        sectionId = section;
        studentUserId = student;
        enrollmentId = enrollment;
        gradingPolicyId = policy;
        academicYearName = yearName;
        examinationName = examName;
        this.gradeName = gradeName;
        this.sectionName = sectionName;
        rollNumber = roll;
        resultPublishedAt = resultPublished;
        this.policyName = policyName;
        this.policyVersion = policyVersion;
        subjectPassPercentage = subjectPass;
        overallPassPercentage = overallPass;
        absentCountsAsFailure = absentFails;
        requireEverySubjectToPass = every;
        decimalScale = scale;
        roundingMode = rounding;
        if (rows == null || rows.isEmpty())
            throw new InvalidResultException("Report card must contain at least one subject");
        if (absentCount < 0 || absentCount > rows.size())
            throw new InvalidResultException("Absent subject count cannot exceed subject count");
        subjects.addAll(rows);
        totalMaximumMarks = totalMax;
        totalObtainedMarks = totalObtained;
        aggregatePercentage = aggregate;
        this.overallGrade = overallGrade;
        this.passed = passed;
        absentSubjectCount = absentCount;
        this.partial = partial;
        createdByUserId = actor;
    }

    public void submit(long expected) {
        expect(expected);
        if (status != ReportCardStatus.DRAFT)
            throw new ResultConflictException("Only a draft report card can be submitted");
        status = ReportCardStatus.IN_REVIEW;
        returnReason = null;
    }

    public void approve(long expected, long actor) {
        expect(expected);
        if (status != ReportCardStatus.IN_REVIEW)
            throw new ResultConflictException("Only an in-review report card can be approved");
        status = ReportCardStatus.APPROVED;
        reviewedByUserId = actor;
    }

    public void returnToDraft(long expected, long actor, String reason) {
        expect(expected);
        if (status != ReportCardStatus.IN_REVIEW && status != ReportCardStatus.APPROVED)
            throw new ResultConflictException("Only an in-review or approved report card can be returned");
        if (reason == null || reason.isBlank()) throw new InvalidResultException("Return reason is required");
        returnReason = reason.trim();
        status = ReportCardStatus.DRAFT;
        reviewedByUserId = actor;
    }

    public void publish(long expected, long actor) {
        expect(expected);
        if (status != ReportCardStatus.APPROVED)
            throw new ResultConflictException("Only an approved report card can be published");
        status = ReportCardStatus.PUBLISHED;
        publishedByUserId = actor;
        publishedAt = OffsetDateTime.now();
    }

    private void expect(long expected) {
        if (!Objects.equals(version, expected))
            throw new ResultConflictException("Report card was updated by another user");
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getPublicationId() {
        return publicationId;
    }

    public Long getAcademicYearId() {
        return academicYearId;
    }

    public Long getExaminationId() {
        return examinationId;
    }

    public Long getGradeLevelId() {
        return gradeLevelId;
    }

    public Long getSectionId() {
        return sectionId;
    }

    public Long getStudentUserId() {
        return studentUserId;
    }

    public Long getEnrollmentId() {
        return enrollmentId;
    }

    public ReportCardStatus getStatus() {
        return status;
    }

    public String getAcademicYearName() {
        return academicYearName;
    }

    public String getExaminationName() {
        return examinationName;
    }

    public String getGradeName() {
        return gradeName;
    }

    public String getSectionName() {
        return sectionName;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public OffsetDateTime getResultPublishedAt() {
        return resultPublishedAt;
    }

    public String getPolicyName() {
        return policyName;
    }

    public Integer getPolicyVersion() {
        return policyVersion;
    }

    public BigDecimal getSubjectPassPercentage() {
        return subjectPassPercentage;
    }

    public BigDecimal getOverallPassPercentage() {
        return overallPassPercentage;
    }

    public Boolean getAbsentCountsAsFailure() {
        return absentCountsAsFailure;
    }

    public Boolean getRequireEverySubjectToPass() {
        return requireEverySubjectToPass;
    }

    public Integer getDecimalScale() {
        return decimalScale;
    }

    public RoundingMode getRoundingMode() {
        return roundingMode;
    }

    public List<ReportCardSubject> getSubjects() {
        return List.copyOf(subjects);
    }

    public BigDecimal getTotalMaximumMarks() {
        return totalMaximumMarks;
    }

    public BigDecimal getTotalObtainedMarks() {
        return totalObtainedMarks;
    }

    public BigDecimal getAggregatePercentage() {
        return aggregatePercentage;
    }

    public String getOverallGrade() {
        return overallGrade;
    }

    public Boolean getPassed() {
        return passed;
    }

    public Integer getAbsentSubjectCount() {
        return absentSubjectCount;
    }

    public Boolean getPartial() {
        return partial;
    }

    public String getReturnReason() {
        return returnReason;
    }

    public Long getVersion() {
        return version;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }
}
