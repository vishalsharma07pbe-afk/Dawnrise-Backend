package com.dawnrise.academic.grading.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record GradeBandRequest(@NotBlank @Size(max = 30) String label,
                               @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal minimumPercentage,
                               @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal maximumPercentage,
                               @Positive int displayOrder, @Size(max = 250) String description) {
}
