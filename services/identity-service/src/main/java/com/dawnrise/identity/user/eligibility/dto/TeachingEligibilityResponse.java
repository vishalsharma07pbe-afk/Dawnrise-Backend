package com.dawnrise.identity.user.eligibility.dto;

import com.dawnrise.identity.user.eligibility.enums.TeachingEligibilityReason;

public record TeachingEligibilityResponse(
        Long userId,
        Long organizationId,
        String displayName,
        boolean eligible,
        TeachingEligibilityReason reason
) {
}