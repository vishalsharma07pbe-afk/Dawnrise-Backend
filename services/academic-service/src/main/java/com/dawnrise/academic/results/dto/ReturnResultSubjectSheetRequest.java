package com.dawnrise.academic.results.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

public record ReturnResultSubjectSheetRequest(
        @NotNull @PositiveOrZero Long version,
        @Size(max = 500) String note
) {}
