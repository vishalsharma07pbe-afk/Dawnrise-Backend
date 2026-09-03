package com.dawnrise.identity.user.eligibility.controller;

import com.dawnrise.identity.user.eligibility.dto.BatchEligibilityRequest;
import jakarta.validation.ValidationException;

import java.util.HashSet;
import java.util.Set;

final class BatchEligibilityRequestValidator {

    private BatchEligibilityRequestValidator() {
    }

    static void rejectDuplicateUserIds(
            BatchEligibilityRequest request
    ) {
        Set<Long> seen = new HashSet<>();

        for (Long userId : request.userIds()) {
            if (userId != null && !seen.add(userId)) {
                throw new ValidationException(
                        "Duplicate user IDs are not allowed"
                );
            }
        }
    }
}
