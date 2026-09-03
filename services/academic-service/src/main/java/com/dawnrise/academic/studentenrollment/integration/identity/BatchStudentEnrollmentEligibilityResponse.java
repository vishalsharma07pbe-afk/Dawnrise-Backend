package com.dawnrise.academic.studentenrollment.integration.identity;

import java.util.List;

public record BatchStudentEnrollmentEligibilityResponse(
        List<StudentEnrollmentEligibilityResponse> results
) {
}
