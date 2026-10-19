package com.dawnrise.academic.grading.entity;

import com.dawnrise.academic.grading.dto.GradeBandRequest;
import com.dawnrise.academic.grading.dto.GradingPolicyRequest;
import com.dawnrise.academic.grading.enums.GradingPolicyStatus;
import com.dawnrise.academic.results.exception.InvalidResultException;
import com.dawnrise.academic.results.exception.ResultConflictException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "grading_policies")
public class GradingPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;
    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;
    @Column(name = "grade_level_id", nullable = false)
    private Long gradeLevelId;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 500)
    private String notes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradingPolicyStatus status = GradingPolicyStatus.DRAFT;
    @Column(name = "subject_pass_percentage", precision = 9, scale = 6, nullable = false)
    private BigDecimal subjectPassPercentage;
    @Column(name = "overall_pass_percentage", precision = 9, scale = 6, nullable = false)
    private BigDecimal overallPassPercentage;
    @Column(name = "absent_counts_as_failure", nullable = false)
    private Boolean absentCountsAsFailure;
    @Column(name = "require_every_subject_to_pass", nullable = false)
    private Boolean requireEverySubjectToPass;
    @Column(name = "decimal_scale", nullable = false)
    private Integer decimalScale;
    @Enumerated(EnumType.STRING)
    @Column(name = "rounding_mode", nullable = false, length = 30)
    private RoundingMode roundingMode;
    @Column(name = "policy_version", nullable = false)
    private Integer policyVersion = 0;
    @Version
    @Column(nullable = false)
    private Long version;
    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;
    @Column(name = "updated_by_user_id", nullable = false)
    private Long updatedByUserId;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "policy_id", nullable = false)
    @OrderBy("displayOrder ASC")
    private List<GradingPolicyBand> bands = new ArrayList<>();

    protected GradingPolicy() {
    }

    public GradingPolicy(long organizationId, long actorUserId, GradingPolicyRequest request) {
        this.organizationId = organizationId;
        this.createdByUserId = actorUserId;
        apply(request, actorUserId);
    }

    public void update(GradingPolicyRequest request, long actorUserId) {
        if (status != GradingPolicyStatus.DRAFT) {
            throw new ResultConflictException("Only a draft grading policy can be edited");
        }
        if (request.version() != null && !Objects.equals(version, request.version())) {
            throw new ResultConflictException("Grading policy was updated by another user");
        }
        apply(request, actorUserId);
    }

    private void apply(GradingPolicyRequest request, long actorUserId) {
        validateConfiguration(request);
        academicYearId = request.academicYearId();
        gradeLevelId = request.gradeLevelId();
        name = request.name().trim();
        notes = request.notes() == null ? null : request.notes().trim();
        subjectPassPercentage = request.subjectPassPercentage();
        overallPassPercentage = request.overallPassPercentage();
        absentCountsAsFailure = request.absentCountsAsFailure();
        requireEverySubjectToPass = request.requireEverySubjectToPass();
        decimalScale = request.decimalScale();
        roundingMode = request.roundingMode();
        updatedByUserId = actorUserId;
        bands.clear();
        request.bands().forEach(band -> bands.add(new GradingPolicyBand(
                organizationId, band.label(), band.minimumPercentage(), band.maximumPercentage(),
                band.displayOrder(), band.description())));
    }

    public void activate(long actorUserId, int effectiveVersion) {
        if (status != GradingPolicyStatus.DRAFT) {
            throw new ResultConflictException("Only a draft grading policy can be activated");
        }
        if (effectiveVersion <= 0) {
            throw new InvalidResultException("Policy version must be positive");
        }
        validateBands(asRequests(), decimalScale, true);
        policyVersion = effectiveVersion;
        status = GradingPolicyStatus.ACTIVE;
        updatedByUserId = actorUserId;
    }

    public void retire(long actorUserId) {
        status = GradingPolicyStatus.RETIRED;
        updatedByUserId = actorUserId;
    }

    private void validateConfiguration(GradingPolicyRequest request) {
        if (request.roundingMode() == RoundingMode.UNNECESSARY) {
            throw new InvalidResultException("UNNECESSARY is not supported for grading calculations");
        }
        validateScale(request.subjectPassPercentage(), request.decimalScale(), "Subject pass percentage");
        validateScale(request.overallPassPercentage(), request.decimalScale(), "Overall pass percentage");
        validateBands(request.bands(), request.decimalScale(), false);
    }

    private static void validateBands(List<GradeBandRequest> rows, int scale, boolean complete) {
        Set<String> labels = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        List<GradeBandRequest> sorted = rows.stream()
                .sorted(Comparator.comparing(GradeBandRequest::minimumPercentage))
                .toList();
        BigDecimal next = BigDecimal.ZERO.setScale(scale);
        BigDecimal previousMaximum = null;
        BigDecimal unit = BigDecimal.ONE.movePointLeft(scale);
        for (GradeBandRequest band : sorted) {
            validateScale(band.minimumPercentage(), scale, "Grade-band minimum");
            validateScale(band.maximumPercentage(), scale, "Grade-band maximum");
            boolean invalid = band.minimumPercentage().compareTo(band.maximumPercentage()) > 0
                    || !labels.add(band.label().trim().toLowerCase(Locale.ROOT))
                    || !orders.add(band.displayOrder())
                    || previousMaximum != null
                    && band.minimumPercentage().compareTo(previousMaximum) <= 0;
            if (invalid) {
                throw new InvalidResultException("Grade bands overlap or contain duplicate labels/order");
            }
            if (complete && band.minimumPercentage().compareTo(next) != 0) {
                throw new InvalidResultException("Active grade bands must cover 0–100 without gaps");
            }
            previousMaximum = band.maximumPercentage();
            next = previousMaximum.add(unit);
        }
        if (complete && (sorted.isEmpty()
                || previousMaximum.compareTo(new BigDecimal("100").setScale(scale)) != 0)) {
            throw new InvalidResultException("Active grade bands must cover 0–100");
        }
    }

    private static void validateScale(BigDecimal value, int scale, String field) {
        if (value.scale() > scale) {
            throw new InvalidResultException(field + " exceeds the configured decimal scale");
        }
    }

    private List<GradeBandRequest> asRequests() {
        return bands.stream().map(band -> new GradeBandRequest(
                band.getLabel(), band.getMinimumPercentage(), band.getMaximumPercentage(),
                band.getDisplayOrder(), band.getDescription())).toList();
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getAcademicYearId() {
        return academicYearId;
    }

    public Long getGradeLevelId() {
        return gradeLevelId;
    }

    public String getName() {
        return name;
    }

    public String getNotes() {
        return notes;
    }

    public GradingPolicyStatus getStatus() {
        return status;
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

    public Integer getPolicyVersion() {
        return policyVersion;
    }

    public Long getVersion() {
        return version;
    }

    public List<GradeBandRequest> bandResponses() {
        return asRequests();
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
