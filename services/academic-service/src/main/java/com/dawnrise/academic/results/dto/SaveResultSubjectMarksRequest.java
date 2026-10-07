package com.dawnrise.academic.results.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record SaveResultSubjectMarksRequest(
        @NotNull @PositiveOrZero Long sheetVersion,
        @NotEmpty List<@Valid ResultSubjectMarkEntryRequest> marks
) {}
