package com.dawnrise.academic.reportcard.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record ReportCardVersionRequest(@PositiveOrZero long version) {
}
