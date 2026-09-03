package com.dawnrise.identity.user.eligibility.service;

import com.dawnrise.identity.user.eligibility.dto.StudentEnrollmentEligibilityResponse;

import java.util.List;

public interface StudentEnrollmentEligibilityService {

    StudentEnrollmentEligibilityResponse check(
            long organizationId,
            long userId
    );

    List<StudentEnrollmentEligibilityResponse> checkBatch(
            long organizationId,
            List<Long> userIds
    );
}
