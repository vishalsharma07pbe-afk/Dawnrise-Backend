package com.dawnrise.identity.user.eligibility.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchEligibilityRequest(
        @NotEmpty(message = "User IDs are required")
        @Size(max = 100, message = "At most 100 user IDs can be checked")
        List<@Positive(message = "User IDs must be positive") Long> userIds
) {
}
