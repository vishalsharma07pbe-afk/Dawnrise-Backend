package com.dawnrise.academic.results.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ResultSubjectMarkEntryRequest(
        @NotNull @Positive Long studentEnrollmentId,
        @NotNull Boolean absent,
        @DecimalMin("0.00") @DecimalMax("99999.99")
        @Digits(integer = 5, fraction = 2) BigDecimal marksObtained,
        @NotNull @PositiveOrZero Long version
) {}
