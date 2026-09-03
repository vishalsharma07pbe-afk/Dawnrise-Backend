package com.dawnrise.academic.common.integration.identity;

import java.util.List;

public record BatchIdentityEligibilityRequest(
        List<Long> userIds
) {
}
