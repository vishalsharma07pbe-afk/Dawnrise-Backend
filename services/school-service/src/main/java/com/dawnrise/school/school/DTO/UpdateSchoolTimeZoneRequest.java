package com.dawnrise.school.school.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSchoolTimeZoneRequest(
        @NotBlank(message = "Time zone ID is required")
        @Size(
                max = 64,
                message = "Time zone ID cannot exceed 64 characters"
        )
        String timeZoneId
) {
}
