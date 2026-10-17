package com.dawnrise.identity.platform.auth.refreshtoken.service;

import com.dawnrise.identity.auth.refreshtoken.exception.InvalidRefreshTokenException;
import com.dawnrise.identity.auth.refreshtoken.security.RefreshTokenCodec;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.platform.auth.refreshtoken.config.PlatformRefreshTokenProperties;
import com.dawnrise.identity.platform.auth.refreshtoken.entity.PlatformRefreshToken;
import com.dawnrise.identity.platform.auth.refreshtoken.model.PlatformRefreshTokenRotationResult;
import com.dawnrise.identity.platform.auth.refreshtoken.model.PlatformRefreshTokenCreationResult;
import com.dawnrise.identity.platform.auth.refreshtoken.model.PlatformRefreshTokenSession;
import com.dawnrise.identity.platform.auth.refreshtoken.repository.PlatformRefreshTokenRepository;
import com.dawnrise.identity.platform.user.entity.PlatformUser;
import com.dawnrise.identity.platform.user.enums.PlatformUserStatus;
import com.dawnrise.identity.platform.user.repository.PlatformUserRepository;
import com.dawnrise.identity.auth.model.IdentityType;
import com.dawnrise.identity.auth.session.service.AuthenticatedSessionService;
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
    private final AuthenticatedSessionService authenticatedSessionService;

    public PlatformRefreshTokenServiceImpl(
            PlatformRefreshTokenRepository refreshTokenRepository,
            RefreshTokenCodec tokenCodec,
            PlatformRefreshTokenProperties tokenProperties,
            PlatformUserRepository platformUserRepository,
            AuthenticatedSessionService authenticatedSessionService
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenCodec = tokenCodec;
        this.tokenProperties = tokenProperties;
        this.platformUserRepository = platformUserRepository;
        this.authenticatedSessionService = authenticatedSessionService;
    }

    @Override
    @Transactional
    public String createRefreshToken(Long platformUserId) {
        return createRefreshTokenSession(platformUserId).rawRefreshToken();
    }

    @Override
    @Transactional
    public PlatformRefreshTokenCreationResult createRefreshTokenSession(Long platformUserId) {
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

        UUID tokenFamilyId = UUID.randomUUID();
        refreshTokenRepository.save(new PlatformRefreshToken(
                platformUserId,
                tokenFamilyId,
                tokenHash,
                currentTime.plus(tokenProperties.getExpiration()),
                familyExpiresAt
        ));

        return new PlatformRefreshTokenCreationResult(rawToken, tokenFamilyId);
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
                        existingToken.getPlatformUserId(),
                        currentTime
                );
            }

            throw invalidToken();
        }

        if (existingToken.isExpired(currentTime)) {
            revokeTokenFamily(existingToken.getTokenFamilyId(), existingToken.getPlatformUserId(), currentTime);
            throw invalidToken();
        }

        if (existingToken.isFamilyExpired(currentTime)) {
            revokeTokenFamily(
                    existingToken.getTokenFamilyId(),
                    existingToken.getPlatformUserId(),
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
                    existingToken.getPlatformUserId(),
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
                replacementRawToken,
                existingToken.getTokenFamilyId()
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
                        revokeTokenFamily(token.getTokenFamilyId(), token.getPlatformUserId(), OffsetDateTime.now())
                );
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public PlatformRefreshTokenSession resolveRefreshTokenSession(String rawRefreshToken) {
        String tokenHash;
        try {
            tokenHash = tokenCodec.hash(rawRefreshToken);
        } catch (IllegalArgumentException exception) {
            throw invalidToken();
        }
        PlatformRefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(this::invalidToken);
        OffsetDateTime now = OffsetDateTime.now();
        if (token.isRevoked()) {
            if (token.getReplacedByTokenHash() != null) {
                revokeTokenFamily(token.getTokenFamilyId(), token.getPlatformUserId(), now);
            }
            throw invalidToken();
        }

        if (token.isExpired(now) || token.isFamilyExpired(now)) {
            revokeTokenFamily(token.getTokenFamilyId(), token.getPlatformUserId(), now);
            throw invalidToken();
        }
        PlatformUser user = platformUserRepository.findById(token.getPlatformUserId())
                .filter(candidate -> candidate.getStatus() == PlatformUserStatus.ACTIVE)
                .orElse(null);
        if (user == null) {
            revokeTokenFamily(token.getTokenFamilyId(), token.getPlatformUserId(), now);
            throw invalidToken();
        }
        return new PlatformRefreshTokenSession(user.getId(), token.getTokenFamilyId());
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
        authenticatedSessionService.retireAllForPlatformUser(platformUserId, currentTime);
    }

    @Override
    @Transactional
    public void revokeTokenFamily(
            UUID tokenFamilyId,
            Long platformUserId,
            OffsetDateTime revokedAt
    ) {
        List<PlatformRefreshToken> activeFamilyTokens =
                refreshTokenRepository
                        .findAllByTokenFamilyIdAndRevokedAtIsNull(
                                tokenFamilyId
                        );

        activeFamilyTokens.forEach(token -> token.revoke(revokedAt));
        authenticatedSessionService.retireIfPresent(
                tokenFamilyId,
                IdentityType.PLATFORM_USER,
                platformUserId,
                revokedAt
        );
    }

    private InvalidRefreshTokenException invalidToken() {
        return new InvalidRefreshTokenException(
                INVALID_TOKEN_MESSAGE
        );
    }
}
