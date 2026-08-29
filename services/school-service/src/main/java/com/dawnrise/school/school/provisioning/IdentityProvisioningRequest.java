package com.dawnrise.school.school.provisioning;

public record IdentityProvisioningRequest(
        Long organizationId,
        String schoolCode,
        String schoolName,
        String schoolEmail,
        IdentityInitialAuthorityRequest authority
) {
}