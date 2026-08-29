package com.dawnrise.identity.auth.refreshtoken.service;

import com.dawnrise.identity.auth.refreshtoken.config.RefreshTokenProperties;
import com.dawnrise.identity.auth.refreshtoken.entity.RefreshToken;
import com.dawnrise.identity.auth.refreshtoken.exception.InvalidRefreshTokenException;
import com.dawnrise.identity.auth.refreshtoken.model.RefreshTokenRotationResult;
import com.dawnrise.identity.auth.refreshtoken.model.RefreshTokenSessionLifetime;
import com.dawnrise.identity.auth.refreshtoken.policy.RefreshTokenSessionLifetimePolicy;
import com.dawnrise.identity.auth.refreshtoken.repository.RefreshTokenRepository;
import com.dawnrise.identity.auth.refreshtoken.security.RefreshTokenCodec;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl
        implements RefreshTokenService {

    private static final String INVALID_TOKEN_MESSAGE =
            "The refresh token is invalid or expired";

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCodec tokenCodec;
    private final RefreshTokenProperties tokenProperties;
    private final RefreshTokenSessionLifetimePolicy sessionLifetimePolicy;
    private final UserRepository userRepository;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenCodec tokenCodec,
            RefreshTokenProperties tokenProperties,
            RefreshTokenSessionLifetimePolicy sessionLifetimePolicy,
            UserRepository userRepository
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenCodec = tokenCodec;
        this.tokenProperties = tokenProperties;
        this.sessionLifetimePolicy = sessionLifetimePolicy;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public String createRefreshToken(Long userId) {
        // Refresh sessions are issued only for currently active users.
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found"
                ));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalidToken();
        }

        String rawToken = tokenCodec.generateRawToken();
        String tokenHash = tokenCodec.hash(rawToken);
        OffsetDateTime currentTime = OffsetDateTime.now();
        RefreshTokenSessionLifetime sessionLifetime =
                sessionLifetimePolicy.lifetimeFor(user);
        OffsetDateTime familyExpiresAt = currentTime.plus(
                sessionLifetime.absoluteSessionLifetime()
        );

        // Store only the token hash; the raw token is returned for the secure cookie.
        RefreshToken refreshToken =
                new RefreshToken(
                        userId,
                        UUID.randomUUID(),
                        tokenHash,
                        currentTime.plus(
                                sessionLifetime.inactivityExpiration()
                        ),
                        familyExpiresAt
                );

        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    @Override
    @Transactional(
            noRollbackFor = InvalidRefreshTokenException.class
    )
    public RefreshTokenRotationResult rotateRefreshToken(
            String rawRefreshToken
    ) {
        String submittedTokenHash;

        try {
            submittedTokenHash =
                    tokenCodec.hash(rawRefreshToken);
        } catch (IllegalArgumentException exception) {
            throw invalidToken();
        }

        RefreshToken existingToken =
                refreshTokenRepository
                        .findByTokenHash(submittedTokenHash)
                        .orElseThrow(this::invalidToken);

        OffsetDateTime currentTime = OffsetDateTime.now();

        if (existingToken.isRevoked()) {
            // Reuse of a rotated token indicates theft, so revoke the family.
            if (existingToken.getReplacedByTokenHash()
                    != null) {
                revokeTokenFamily(
                        existingToken.getTokenFamilyId(),
                        currentTime
                );
            }

            throw invalidToken();
        }

        if (existingToken.isExpired(currentTime)) {
            // Expired individual tokens cannot be rotated.
            existingToken.revoke(currentTime);
            throw invalidToken();
        }

        if (existingToken.isFamilyExpired(currentTime)) {
            // Absolute session lifetime ends the whole token family.
            revokeTokenFamily(
                    existingToken.getTokenFamilyId(),
                    currentTime
            );

            throw invalidToken();
        }

        User user = userRepository
                .findById(existingToken.getUserId())
                .orElseThrow(this::invalidToken);

        if (user.getStatus() != UserStatus.ACTIVE) {
            // Status changes invalidate the whole session family.
            revokeTokenFamily(
                    existingToken.getTokenFamilyId(),
                    currentTime
            );

            throw invalidToken();
        }

        String replacementRawToken =
                tokenCodec.generateRawToken();

        String replacementTokenHash =
                tokenCodec.hash(replacementRawToken);

        OffsetDateTime familyExpiresAt =
                existingToken.getFamilyExpiresAt();
        RefreshTokenSessionLifetime sessionLifetime =
                sessionLifetimePolicy.lifetimeFor(user);

        // Replacement tokens preserve the original absolute family expiry.
        OffsetDateTime rollingExpiry =
                currentTime.plus(
                        sessionLifetime.inactivityExpiration()
                );

        OffsetDateTime effectiveExpiry =
                rollingExpiry.isBefore(familyExpiresAt)
                        ? rollingExpiry
                        : familyExpiresAt;

        RefreshToken replacementToken =
                new RefreshToken(
                        user.getId(),
                        existingToken.getTokenFamilyId(),
                        replacementTokenHash,
                        effectiveExpiry,
                        familyExpiresAt
                );

        existingToken.rotate(
                currentTime,
                replacementTokenHash
        );

        refreshTokenRepository.save(replacementToken);

        return new RefreshTokenRotationResult(
                user.getId(),
                replacementRawToken
        );
    }

    @Override
    @Transactional
    public void revokeRefreshToken(
            String rawRefreshToken
    ) {
        // Logout is idempotent; missing or malformed cookies are ignored.
        if (rawRefreshToken == null
                || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash;

        try {
            tokenHash = tokenCodec.hash(rawRefreshToken);
        } catch (IllegalArgumentException exception) {
            return;
        }

        refreshTokenRepository
                .findByTokenHash(tokenHash)
                .ifPresent(token ->
                        token.revoke(OffsetDateTime.now())
                );
    }

    @Override
    @Transactional
    public void revokeAllForUser(Long userId) {
        // Used after password changes and account suspension/inactivation.
        List<RefreshToken> activeTokens =
                refreshTokenRepository
                        .findAllByUserIdAndRevokedAtIsNull(
                                userId
                        );

        OffsetDateTime currentTime = OffsetDateTime.now();

        activeTokens.forEach(token ->
                token.revoke(currentTime)
        );
    }

    private void revokeTokenFamily(
            UUID tokenFamilyId,
            OffsetDateTime revokedAt
    ) {
        List<RefreshToken> activeFamilyTokens =
                refreshTokenRepository
                        .findAllByTokenFamilyIdAndRevokedAtIsNull(
                                tokenFamilyId
                        );

        activeFamilyTokens.forEach(token ->
                token.revoke(revokedAt)
        );
    }

    private InvalidRefreshTokenException invalidToken() {
        return new InvalidRefreshTokenException(
                INVALID_TOKEN_MESSAGE
        );
    }
}
