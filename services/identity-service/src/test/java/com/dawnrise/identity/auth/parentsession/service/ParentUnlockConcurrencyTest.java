package com.dawnrise.identity.auth.parentsession.service;

import com.dawnrise.identity.auth.config.PasswordChangeProperties;
import com.dawnrise.identity.auth.lockout.LoginLockoutService;
import com.dawnrise.identity.auth.parentsession.config.ParentSessionProperties;
import com.dawnrise.identity.auth.parentsession.entity.ParentSession;
import com.dawnrise.identity.auth.parentsession.repository.ParentSessionRepository;
import com.dawnrise.identity.auth.refreshtoken.config.RefreshTokenProperties;
import com.dawnrise.identity.auth.refreshtoken.entity.RefreshToken;
import com.dawnrise.identity.auth.refreshtoken.exception.InvalidRefreshTokenException;
import com.dawnrise.identity.auth.refreshtoken.policy.RefreshTokenSessionLifetimePolicy;
import com.dawnrise.identity.auth.refreshtoken.repository.RefreshTokenRepository;
import com.dawnrise.identity.auth.refreshtoken.security.RefreshTokenCodec;
import com.dawnrise.identity.auth.refreshtoken.service.RefreshTokenServiceImpl;
import com.dawnrise.identity.auth.security.JwtService;
import com.dawnrise.identity.auth.service.AuthServiceImpl;
import com.dawnrise.identity.permission.service.PermissionService;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        RefreshTokenServiceImpl.class,
        ParentSessionService.class,
        AuthServiceImpl.class,
        RefreshTokenCodec.class,
        ParentUnlockConcurrencyTest.Configuration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ParentUnlockConcurrencyTest {

    @Autowired
    private AuthServiceImpl authService;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private ParentSessionRepository parentSessions;

    @Autowired
    private UserRepository users;

    @Autowired
    private RefreshTokenCodec tokenCodec;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private LoginLockoutService loginLockoutService;

    @MockitoBean
    private PasswordChangeProperties passwordChangeProperties;

    @MockitoBean
    private PermissionService permissionService;

    @MockitoBean
    private SecurityAuditService securityAuditService;

    @Test
    void simultaneousUnlocksWithSameCookie_createOnlyOneReplacement()
            throws Exception {
        User parent = new User(
                1L,
                "parent01",
                "Parent",
                Set.of(UserRole.PARENT)
        );
        parent.setStatus(UserStatus.ACTIVE);
        parent.resetPassword("encoded-password");
        parent = users.saveAndFlush(parent);

        String rawRefreshToken = "shared-parent-refresh-token";
        UUID oldFamilyId = UUID.randomUUID();
        RefreshToken oldToken = refreshTokens.saveAndFlush(new RefreshToken(
                parent.getId(),
                oldFamilyId,
                tokenCodec.hash(rawRefreshToken),
                OffsetDateTime.now().plusHours(1),
                OffsetDateTime.now().plusDays(90)
        ));
        parentSessions.saveAndFlush(new ParentSession(
                oldFamilyId,
                parent.getId(),
                OffsetDateTime.now().minusMinutes(20)
        ));

        when(passwordEncoder.matches("secret", "encoded-password"))
                .thenReturn(true);
        when(permissionService.getActivePermissionsForRoles(anySet()))
                .thenReturn(Set.of());
        when(jwtService.generateAccessToken(any(), anySet(), any(), any()))
                .thenReturn("replacement-access");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Class<?>> first = executor.submit(() ->
                    unlockAfter(ready, start, rawRefreshToken)
            );
            Future<Class<?>> second = executor.submit(() ->
                    unlockAfter(ready, start, rawRefreshToken)
            );
            ready.await();
            start.countDown();

            List<Class<?>> outcomes = List.of(first.get(), second.get());
            assertEquals(1L, outcomes.stream()
                    .filter(Void.class::equals)
                    .count());
            assertEquals(1L, outcomes.stream()
                    .filter(InvalidRefreshTokenException.class::equals)
                    .count());
        }

        RefreshToken persistedOldToken = refreshTokens
                .findById(oldToken.getId())
                .orElseThrow();
        assertTrue(persistedOldToken.isRevoked());
        assertEquals(1L, refreshTokens.findAll().stream()
                .filter(token -> !token.isRevoked())
                .count());
        assertEquals(1L, parentSessions.findAll().stream()
                .filter(session -> !session.isLocked())
                .count());
    }

    private Class<?> unlockAfter(
            CountDownLatch ready,
            CountDownLatch start,
            String rawRefreshToken
    ) throws InterruptedException {
        ready.countDown();
        start.await();

        try {
            authService.unlockParent(rawRefreshToken, "secret");
            return Void.class;
        } catch (RuntimeException exception) {
            return exception.getClass();
        }
    }

    @TestConfiguration
    static class Configuration {

        @Bean
        RefreshTokenProperties refreshTokenProperties() {
            RefreshTokenProperties properties = new RefreshTokenProperties();
            properties.setExpiration(Duration.ofDays(30));
            properties.setAbsoluteSessionLifetime(Duration.ofDays(90));
            properties.setStandardSessionRoles(List.of(UserRole.PARENT));
            properties.setStandardSessionExpiration(Duration.ofDays(30));
            properties.setStandardSessionAbsoluteLifetime(Duration.ofDays(90));
            properties.setShortSessionRoles(List.of(UserRole.ADMIN));
            properties.setShortSessionExpiration(Duration.ofDays(7));
            properties.setShortSessionAbsoluteLifetime(Duration.ofDays(30));
            return properties;
        }

        @Bean
        RefreshTokenSessionLifetimePolicy refreshTokenSessionLifetimePolicy(
                RefreshTokenProperties properties
        ) {
            return new RefreshTokenSessionLifetimePolicy(properties);
        }

        @Bean
        ParentSessionProperties parentSessionProperties() {
            ParentSessionProperties properties = new ParentSessionProperties();
            properties.setInactivityTimeout(Duration.ofMinutes(15));
            properties.setRecentAuthentication(Duration.ofMinutes(10));
            return properties;
        }
    }
}
