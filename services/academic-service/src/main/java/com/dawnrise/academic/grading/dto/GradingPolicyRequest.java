package com.dawnrise.academic.grading.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.*;
import java.util.List;

public record GradingPolicyRequest(@Positive long academicYearId, @Positive long gradeLevelId,
                                   @NotBlank @Size(max = 120) String name, @Size(max = 500) String notes,
                                   @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal subjectPassPercentage,
                                   @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal overallPassPercentage,
                                   boolean absentCountsAsFailure, boolean requireEverySubjectToPass,
                                   @Min(0) @Max(6) int decimalScale, @NotNull RoundingMode roundingMode,
                                   @NotEmpty List<@Valid GradeBandRequest> bands, @PositiveOrZero Long version) {
}
