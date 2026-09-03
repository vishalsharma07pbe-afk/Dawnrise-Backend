package com.dawnrise.identity.user.eligibility.dto;

import java.util.List;

public record BatchTeachingEligibilityResponse(
        List<TeachingEligibilityResponse> results
) {
}
