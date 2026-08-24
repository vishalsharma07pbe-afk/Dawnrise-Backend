package com.edusphere.identity.platform.auth.refreshtoken.service;

import com.edusphere.identity.auth.refreshtoken.exception.InvalidRefreshTokenException;
import com.edusphere.identity.auth.refreshtoken.security.RefreshTokenCodec;
import com.edusphere.identity.common.exception.ResourceNotFoundException;
import com.edusphere.identity.platform.auth.refreshtoken.config.PlatformRefreshTokenProperties;
import com.edusphere.identity.platform.auth.refreshtoken.entity.PlatformRefreshToken;
import com.edusphere.identity.platform.auth.refreshtoken.model.PlatformRefreshTokenRotationResult;
import com.edusphere.identity.platform.auth.refreshtoken.repository.PlatformRefreshTokenRepository;
import com.edusphere.identity.platform.user.entity.PlatformUser;
import com.edusphere.identity.platform.user.enums.PlatformUserStatus;
import com.edusphere.identity.platform.user.repository.PlatformUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PlatformRefreshTokenServiceImpl
        implements PlatformRefreshTokenService {

    private static final String INVALID_TOKEN_MESSAGE =
            "The refresh token is invalid or expired";

    private final PlatformRefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCodec tokenCodec;
    private final PlatformRefreshTokenProperties tokenProperties;
    private final PlatformUserRepository platformUserRepository;

    public PlatformRefreshTokenServiceImpl(
            PlatformRefreshTokenRepository refreshTokenRepository,
            RefreshTokenCodec tokenCodec,
            PlatformRefreshTokenProperties tokenProperties,
            PlatformUserRepository platformUserRepository
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenCodec = tokenCodec;
        this.tokenProperties = tokenProperties;
        this.platformUserRepository = platformUserRepository;
    }

    @Override
    @Transactional
    public String createRefreshToken(Long platformUserId) {
        PlatformUser platformUser = platformUserRepository
                .findById(platformUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Platform user not found"
                ));

        if (platformUser.getStatus() != PlatformUserStatus.ACTIVE) {
            throw invalidToken();
        }

        String rawToken = tokenCodec.generateRawToken();
        String tokenHash = tokenCodec.hash(rawToken);
        OffsetDateTime currentTime = OffsetDateTime.now();
        OffsetDateTime familyExpiresAt = currentTime.plus(
                tokenProperties.getAbsoluteSessionLifetime()
        );

        refreshTokenRepository.save(new PlatformRefreshToken(
                platformUserId,
                UUID.randomUUID(),
                tokenHash,
                currentTime.plus(tokenProperties.getExpiration()),
                familyExpiresAt
        ));

        return rawToken;
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public PlatformRefreshTokenRotationResult rotateRefreshToken(
            String rawRefreshToken
    ) {
        String submittedTokenHash;

        try {
            submittedTokenHash = tokenCodec.hash(rawRefreshToken);
        } catch (IllegalArgumentException exception) {
            throw invalidToken();
        }

        PlatformRefreshToken existingToken = refreshTokenRepository
                .findByTokenHash(submittedTokenHash)
                .orElseThrow(this::invalidToken);

        OffsetDateTime currentTime = OffsetDateTime.now();

        if (existingToken.isRevoked()) {
            if (existingToken.getReplacedByTokenHash() != null) {
                revokeTokenFamily(
                        existingToken.getTokenFamilyId(),
                        currentTime
                );
            }

            throw invalidToken();
        }

        if (existingToken.isExpired(currentTime)) {
            existingToken.revoke(currentTime);
            throw invalidToken();
        }

        if (existingToken.isFamilyExpired(currentTime)) {
            revokeTokenFamily(
                    existingToken.getTokenFamilyId(),
                    currentTime
            );
            throw invalidToken();
        }

        PlatformUser platformUser = platformUserRepository
                .findById(existingToken.getPlatformUserId())
                .orElseThrow(this::invalidToken);

        if (platformUser.getStatus() != PlatformUserStatus.ACTIVE) {
            revokeTokenFamily(
                    existingToken.getTokenFamilyId(),
                    currentTime
            );
            throw invalidToken();
        }

        String replacementRawToken = tokenCodec.generateRawToken();
        String replacementTokenHash =
                tokenCodec.hash(replacementRawToken);
        OffsetDateTime familyExpiresAt =
                existingToken.getFamilyExpiresAt();
        OffsetDateTime rollingExpiry =
                currentTime.plus(tokenProperties.getExpiration());
        OffsetDateTime effectiveExpiry =
                rollingExpiry.isBefore(familyExpiresAt)
                        ? rollingExpiry
                        : familyExpiresAt;

        existingToken.rotate(currentTime, replacementTokenHash);

        refreshTokenRepository.save(new PlatformRefreshToken(
                platformUser.getId(),
                existingToken.getTokenFamilyId(),
                replacementTokenHash,
                effectiveExpiry,
                familyExpiresAt
        ));

        return new PlatformRefreshTokenRotationResult(
                platformUser.getId(),
                replacementRawToken
        );
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash;

        try {
            tokenHash = tokenCodec.hash(rawRefreshToken);
        } catch (IllegalArgumentException exception) {
            return;
        }

        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token ->
                        token.revoke(OffsetDateTime.now())
                );
    }

    @Override
    @Transactional
    public void revokeAllForPlatformUser(Long platformUserId) {
        List<PlatformRefreshToken> activeTokens =
                refreshTokenRepository
                        .findAllByPlatformUserIdAndRevokedAtIsNull(
                                platformUserId
                        );

        OffsetDateTime currentTime = OffsetDateTime.now();
        activeTokens.forEach(token -> token.revoke(currentTime));
    }

    private void revokeTokenFamily(
            UUID tokenFamilyId,
            OffsetDateTime revokedAt
    ) {
        List<PlatformRefreshToken> activeFamilyTokens =
                refreshTokenRepository
                        .findAllByTokenFamilyIdAndRevokedAtIsNull(
                                tokenFamilyId
                        );

        activeFamilyTokens.forEach(token -> token.revoke(revokedAt));
    }

    private InvalidRefreshTokenException invalidToken() {
        return new InvalidRefreshTokenException(
                INVALID_TOKEN_MESSAGE
        );
    }
}
