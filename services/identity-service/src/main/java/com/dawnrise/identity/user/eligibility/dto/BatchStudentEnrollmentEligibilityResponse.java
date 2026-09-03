package com.dawnrise.identity.user.eligibility.dto;

import java.util.List;

public record BatchStudentEnrollmentEligibilityResponse(
        List<StudentEnrollmentEligibilityResponse> results
) {
}
