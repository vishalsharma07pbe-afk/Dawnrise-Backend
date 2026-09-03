package com.dawnrise.academic.teacherassignment.integration.identity;

public record TeachingEligibilityResponse(
        Long userId,
        Long organizationId,
        String displayName,
        boolean eligible,
        TeachingEligibilityReason reason
) {
}