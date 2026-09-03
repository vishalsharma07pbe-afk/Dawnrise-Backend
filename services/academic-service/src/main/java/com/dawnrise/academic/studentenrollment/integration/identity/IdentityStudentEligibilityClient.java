package com.dawnrise.academic.studentenrollment.integration.identity;

public interface IdentityStudentEligibilityClient {

    StudentEnrollmentEligibilityResponse check(
            long organizationId,
            long userId
    );

    BatchStudentEnrollmentEligibilityResponse checkBatch(
            long organizationId,
            java.util.List<Long> userIds
    );
}
