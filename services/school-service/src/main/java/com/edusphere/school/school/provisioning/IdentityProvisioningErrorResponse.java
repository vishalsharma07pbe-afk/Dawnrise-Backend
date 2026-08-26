package com.edusphere.school.school.provisioning;

import java.util.Map;

public record IdentityProvisioningErrorResponse(
        String message,
        Map<String, String> validationErrors
) {
}