package com.dawnrise.academic.examination.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateExaminationRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @PositiveOrZero Long version
) {}
