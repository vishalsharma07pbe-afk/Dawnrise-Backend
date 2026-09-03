package com.dawnrise.academic.teacherassignment.integration.identity;

public interface IdentityTeacherEligibilityClient {

    TeachingEligibilityResponse check(
            long organizationId,
            long userId
    );

    BatchTeachingEligibilityResponse checkBatch(
            long organizationId,
            java.util.List<Long> userIds
    );
}
