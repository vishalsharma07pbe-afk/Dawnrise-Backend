package com.dawnrise.academic.reportcard.dto;

import jakarta.validation.constraints.*;

public record ReturnReportCardRequest(@PositiveOrZero long version, @NotBlank @Size(max = 500) String reason) {
}
