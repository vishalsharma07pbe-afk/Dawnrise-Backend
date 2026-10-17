package com.dawnrise.identity.auth.session.dto;

import java.time.OffsetDateTime;

public record AuthenticatedSessionValidationResponse(
        boolean active,
        OffsetDateTime authenticatedAt
) {
}
