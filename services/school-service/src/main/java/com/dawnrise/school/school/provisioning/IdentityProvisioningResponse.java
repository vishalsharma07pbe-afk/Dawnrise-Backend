package com.dawnrise.school.school.provisioning;

public record IdentityProvisioningResponse(
        Long organizationId,
        String organizationStatus,
        Long authorityUserId,
        String authorityStatus,
        String provisioningStatus
) {
}