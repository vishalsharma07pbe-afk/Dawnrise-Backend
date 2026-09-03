package com.dawnrise.identity.user.eligibility.service;

import com.dawnrise.identity.user.eligibility.dto.TeachingEligibilityResponse;

public interface TeachingEligibilityService {

    TeachingEligibilityResponse check(
            long organizationId,
            long userId
    );
}