package com.dawnrise.school.school.provisioning;

public record IdentityInitialAuthorityRequest(
        String username,
        String firstName,
        String middleName,
        String lastName,
        String email,
        String phone
) {
}