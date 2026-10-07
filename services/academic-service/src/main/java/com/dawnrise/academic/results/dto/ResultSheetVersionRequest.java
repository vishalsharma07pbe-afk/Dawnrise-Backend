package com.dawnrise.academic.results.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ResultSheetVersionRequest(
        @NotNull @PositiveOrZero Long version
) {}
