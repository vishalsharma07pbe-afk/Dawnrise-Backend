package com.dawnrise.academic.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedAcademicActorTest {

    @Test
    void organizationIdReturnsValidNumericClaim() {
        JwtAuthenticationToken authentication = authentication(10L, "20");

        assertThat(AuthenticatedAcademicActor.organizationId(authentication))
                .isEqualTo(10L);
    }

    @Test
    void userIdReturnsValidNumericSubject() {
        JwtAuthenticationToken authentication = authentication(10L, "20");

        assertThat(AuthenticatedAcademicActor.userId(authentication))
                .isEqualTo(20L);
    }

    @Test
    void organizationIdRejectsMissingClaim() {
        JwtAuthenticationToken authentication = authentication(null, "20");

        assertThatThrownBy(() ->
                AuthenticatedAcademicActor.organizationId(authentication)
        )
                .isInstanceOf(InvalidAuthenticatedAcademicActorException.class)
                .hasMessage("Authenticated token does not contain a valid organization ID");
    }

    @Test
    void organizationIdRejectsZeroOrNegativeClaim() {
        JwtAuthenticationToken zero = authentication(0L, "20");
        JwtAuthenticationToken negative = authentication(-1L, "20");

        assertThatThrownBy(() -> AuthenticatedAcademicActor.organizationId(zero))
                .isInstanceOf(InvalidAuthenticatedAcademicActorException.class)
                .hasMessage("Authenticated token does not contain a valid organization ID");

        assertThatThrownBy(() -> AuthenticatedAcademicActor.organizationId(negative))
                .isInstanceOf(InvalidAuthenticatedAcademicActorException.class)
                .hasMessage("Authenticated token does not contain a valid organization ID");
    }

    @Test
    void userIdRejectsMissingOrBlankSubject() {
        JwtAuthenticationToken missing = authentication(10L, null);
        JwtAuthenticationToken blank = authentication(10L, " ");

        assertThatThrownBy(() -> AuthenticatedAcademicActor.userId(missing))
                .isInstanceOf(InvalidAuthenticatedAcademicActorException.class)
                .hasMessage("Authenticated token does not contain a valid user ID");

        assertThatThrownBy(() -> AuthenticatedAcademicActor.userId(blank))
                .isInstanceOf(InvalidAuthenticatedAcademicActorException.class)
                .hasMessage("Authenticated token does not contain a valid user ID");
    }

    @Test
    void userIdRejectsNonNumericSubject() {
        JwtAuthenticationToken authentication = authentication(10L, "user-20");

        assertThatThrownBy(() ->
                AuthenticatedAcademicActor.userId(authentication)
        )
                .isInstanceOf(InvalidAuthenticatedAcademicActorException.class)
                .hasMessage("Authenticated token does not contain a valid user ID");
    }

    @Test
    void userIdRejectsZeroOrNegativeSubject() {
        JwtAuthenticationToken zero = authentication(10L, "0");
        JwtAuthenticationToken negative = authentication(10L, "-1");

        assertThatThrownBy(() -> AuthenticatedAcademicActor.userId(zero))
                .isInstanceOf(InvalidAuthenticatedAcademicActorException.class)
                .hasMessage("Authenticated token does not contain a valid user ID");

        assertThatThrownBy(() -> AuthenticatedAcademicActor.userId(negative))
                .isInstanceOf(InvalidAuthenticatedAcademicActorException.class)
                .hasMessage("Authenticated token does not contain a valid user ID");
    }

    private static JwtAuthenticationToken authentication(
            Long organizationId,
            String subject
    ) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none");

        if (organizationId != null) {
            builder.claim("organizationId", organizationId);
        }

        if (subject != null) {
            builder.claim("sub", subject);
        }

        return new JwtAuthenticationToken(builder.build());
    }
}
