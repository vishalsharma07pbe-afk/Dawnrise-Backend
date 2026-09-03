package com.dawnrise.academic.teacherassignment.integration.identity;

import java.util.List;

public record BatchTeachingEligibilityResponse(
        List<TeachingEligibilityResponse> results
) {
}
