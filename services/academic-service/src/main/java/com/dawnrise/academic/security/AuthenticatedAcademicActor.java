package com.dawnrise.academic.security;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class AuthenticatedAcademicActor {

    private AuthenticatedAcademicActor() {
    }

    public static long organizationId(JwtAuthenticationToken authentication) {
        Number organizationId =
                authentication.getToken().getClaim("organizationId");
        if (organizationId == null || organizationId.longValue() <= 0) {
            throw new InvalidAuthenticatedAcademicActorException(
                    "Authenticated token does not contain a valid organization ID"
            );
        }
        return organizationId.longValue();
    }

    public static long userId(JwtAuthenticationToken authentication) {
        String subject = authentication.getToken().getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidAuthenticatedAcademicActorException(
                    "Authenticated token does not contain a valid user ID"
            );
        }
        try {
            long userId = Long.parseLong(subject);
            if (userId <= 0) {
                throw new NumberFormatException("User ID must be positive");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new InvalidAuthenticatedAcademicActorException(
                    "Authenticated token does not contain a valid user ID"
            );
        }
    }
}
