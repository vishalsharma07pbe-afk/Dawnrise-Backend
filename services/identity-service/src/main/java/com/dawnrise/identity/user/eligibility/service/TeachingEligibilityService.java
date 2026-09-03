package com.dawnrise.identity.user.eligibility.service;

import com.dawnrise.identity.user.eligibility.dto.TeachingEligibilityResponse;

import java.util.List;

public interface TeachingEligibilityService {

    TeachingEligibilityResponse check(
            long organizationId,
            long userId
    );

    List<TeachingEligibilityResponse> checkBatch(
            long organizationId,
            List<Long> userIds
    );
}
