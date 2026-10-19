package com.dawnrise.academic.reportcard.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "report_card_subjects")
public class ReportCardSubject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;
    @Column(name = "subject_code", nullable = false)
    private String subjectCode;
    @Column(name = "subject_name", nullable = false)
    private String subjectName;
    @Column(name = "maximum_marks", nullable = false)
    private BigDecimal maximumMarks;
    @Column(name = "obtained_marks")
    private BigDecimal obtainedMarks;
    @Column(nullable = false)
    private Boolean absent;
    @Column(precision = 9, scale = 6)
    private BigDecimal percentage;
    @Column(name = "grade_label")
    private String gradeLabel;
    @Column(nullable = false)
    private Boolean passed;
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    protected ReportCardSubject() {
    }

    public ReportCardSubject(long o, long sid, String code, String name, BigDecimal max, BigDecimal obtained, boolean absent, BigDecimal percentage, String label, boolean passed, int order) {
        organizationId = o;
        subjectId = sid;
        subjectCode = code;
        subjectName = name;
        maximumMarks = max;
        obtainedMarks = obtained;
        this.absent = absent;
        this.percentage = percentage;
        gradeLabel = label;
        this.passed = passed;
        displayOrder = order;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public BigDecimal getMaximumMarks() {
        return maximumMarks;
    }

    public BigDecimal getObtainedMarks() {
        return obtainedMarks;
    }

    public Boolean getAbsent() {
        return absent;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public String getGradeLabel() {
        return gradeLabel;
    }

    public Boolean getPassed() {
        return passed;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }
}
