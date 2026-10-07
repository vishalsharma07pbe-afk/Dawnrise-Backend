package com.dawnrise.academic.examination.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateExaminationRequest(
        @NotBlank @Size(max = 120) String name
) {}
