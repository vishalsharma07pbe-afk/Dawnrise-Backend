package com.dawnrise.academic.studentenrollment.integration.identity;

public record StudentEnrollmentEligibilityResponse(
        Long userId,
        Long organizationId,
        String displayName,
        boolean eligible,
        StudentEnrollmentEligibilityReason reason
) {
}