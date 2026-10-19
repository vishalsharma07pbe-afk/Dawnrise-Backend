package com.dawnrise.academic.grading.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "grading_policy_bands")
public class GradingPolicyBand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;
    @Column(nullable = false, length = 30)
    private String label;
    @Column(name = "minimum_percentage", precision = 9, scale = 6, nullable = false)
    private BigDecimal minimumPercentage;
    @Column(name = "maximum_percentage", precision = 9, scale = 6, nullable = false)
    private BigDecimal maximumPercentage;
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
    @Column(length = 250)
    private String description;

    protected GradingPolicyBand() {
    }

    GradingPolicyBand(
            long organizationId,
            String label,
            BigDecimal minimumPercentage,
            BigDecimal maximumPercentage,
            int displayOrder,
            String description
    ) {
        this.organizationId = organizationId;
        this.label = label.trim();
        this.minimumPercentage = minimumPercentage;
        this.maximumPercentage = maximumPercentage;
        this.displayOrder = displayOrder;
        this.description = description == null ? null : description.trim();
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getMinimumPercentage() {
        return minimumPercentage;
    }

    public BigDecimal getMaximumPercentage() {
        return maximumPercentage;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public String getDescription() {
        return description;
    }
}
