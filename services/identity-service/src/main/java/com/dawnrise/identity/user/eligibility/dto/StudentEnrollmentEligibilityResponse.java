package com.dawnrise.identity.user.eligibility.dto;

import com.dawnrise.identity.user.eligibility.enums.StudentEnrollmentEligibilityReason;

public record StudentEnrollmentEligibilityResponse(
        Long userId,
        Long organizationId,
        String displayName,
        boolean eligible,
        StudentEnrollmentEligibilityReason reason
) {
}