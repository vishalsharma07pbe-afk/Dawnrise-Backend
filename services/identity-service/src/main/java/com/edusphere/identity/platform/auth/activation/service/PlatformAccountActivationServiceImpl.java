package com.edusphere.identity.platform.auth.activation.service;

import com.edusphere.identity.auth.activation.config.ActivationTokenProperties;
import com.edusphere.identity.auth.activation.dto.CompleteAccountActivationRequest;
import com.edusphere.identity.auth.activation.dto.ResendActivationRequest;
import com.edusphere.identity.auth.activation.exception.InvalidActivationTokenException;
import com.edusphere.identity.auth.activation.exception.PasswordMismatchException;
import com.edusphere.identity.auth.activation.security.ActivationTokenCodec;
import com.edusphere.identity.common.exception.ResourceNotFoundException;
import com.edusphere.identity.platform.audit.service.PlatformSecurityAuditService;
import com.edusphere.identity.platform.auth.activation.entity.PlatformUserActivationToken;
import com.edusphere.identity.platform.auth.activation.event.PlatformUserActivationRequestedEvent;
import com.edusphere.identity.platform.auth.activation.repository.PlatformUserActivationTokenRepository;
import com.edusphere.identity.platform.user.entity.PlatformUser;
import com.edusphere.identity.platform.user.enums.PlatformUserStatus;
import com.edusphere.identity.platform.user.repository.PlatformUserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.edusphere.identity.securityaudit.enums.SecurityAuditAction;
import com.edusphere.identity.securityaudit.enums.SecurityAuditOutcome;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PlatformAccountActivationServiceImpl
        implements PlatformAccountActivationService {

    private static final String INVALID_TOKEN_MESSAGE =
            "The platform activation token is invalid or expired";

    private final PlatformUserActivationTokenRepository
            activationTokenRepository;

    private final PlatformUserRepository platformUserRepository;
    private final ActivationTokenCodec tokenCodec;
    private final ActivationTokenProperties tokenProperties;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformSecurityAuditService auditService;

    public PlatformAccountActivationServiceImpl(
            PlatformUserActivationTokenRepository activationTokenRepository,
            PlatformUserRepository platformUserRepository,
            ActivationTokenCodec tokenCodec,
            ActivationTokenProperties tokenProperties,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            PlatformSecurityAuditService auditService
    ) {
        this.activationTokenRepository = activationTokenRepository;
        this.platformUserRepository = platformUserRepository;
        this.tokenCodec = tokenCodec;
        this.tokenProperties = tokenProperties;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public String generateActivationToken(
            Long platformUserId
    ) {
        PlatformUser platformUser = platformUserRepository
                .findById(platformUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Platform user not found"
                ));

        if (platformUser.getStatus()
                != PlatformUserStatus.PENDING_ACTIVATION) {
            throw invalidToken();
        }

        OffsetDateTime currentTime = OffsetDateTime.now();

        OffsetDateTime resendWindowStart =
                currentTime.minus(
                        tokenProperties.getResendWindow()
                );

        long emailsGenerated = activationTokenRepository
                .countByPlatformUserIdAndCreatedAtAfter(
                        platformUserId,
                        resendWindowStart
                );

        if (emailsGenerated
                >= tokenProperties.getMaxEmailsPerWindow()) {
            throw new IllegalStateException(
                    "Platform activation email rate limit exceeded"
            );
        }

        List<PlatformUserActivationToken> previousTokens =
                activationTokenRepository
                        .findAllByPlatformUserIdAndUsedAtIsNullAndRevokedAtIsNull(
                                platformUserId
                        );

        previousTokens.forEach(token ->
                token.revoke(currentTime)
        );

        String rawToken = tokenCodec.generateRawToken();
        String tokenHash = tokenCodec.hash(rawToken);

        PlatformUserActivationToken activationToken =
                new PlatformUserActivationToken(
                        platformUserId,
                        tokenHash,
                        currentTime.plus(
                                tokenProperties.getExpiration()
                        )
                );

        activationTokenRepository.save(activationToken);

        return rawToken;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActivationTokenValid(
            String rawToken
    ) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        String tokenHash;

        try {
            tokenHash = tokenCodec.hash(rawToken);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        return activationTokenRepository
                .findByTokenHash(tokenHash)
                .filter(token ->
                        token.isValidAt(OffsetDateTime.now())
                )
                .flatMap(token ->
                        platformUserRepository.findById(
                                token.getPlatformUserId()
                        )
                )
                .map(platformUser ->
                        platformUser.getStatus()
                                == PlatformUserStatus.PENDING_ACTIVATION
                )
                .orElse(false);
    }

    @Override
    @Transactional
    public void completeActivation(
            CompleteAccountActivationRequest request
    ) {
        if (!request.getPassword().equals(
                request.getConfirmPassword()
        )) {
            throw new PasswordMismatchException(
                    "Password and confirmation do not match"
            );
        }

        String tokenHash = tokenCodec.hash(
                request.getToken()
        );

        PlatformUserActivationToken activationToken =
                activationTokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(this::invalidToken);

        OffsetDateTime currentTime = OffsetDateTime.now();

        if (!activationToken.isValidAt(currentTime)) {
            throw invalidToken();
        }

        PlatformUser platformUser = platformUserRepository
                .findById(
                        activationToken.getPlatformUserId()
                )
                .orElseThrow(this::invalidToken);

        if (platformUser.getStatus()
                != PlatformUserStatus.PENDING_ACTIVATION) {
            throw invalidToken();
        }

        String passwordHash = passwordEncoder.encode(
                request.getPassword()
        );

        platformUser.activate(passwordHash);
        activationToken.markUsed(currentTime);

        auditService.record(
                platformUser.getId(),
                SecurityAuditAction.PLATFORM_ACTIVATION_COMPLETE,
                SecurityAuditOutcome.SUCCESS,
                "PLATFORM_USER",
                platformUser.getId(),
                Map.of("status", platformUser.getStatus().name())
        );
    }

    @Override
    @Transactional
    public void requestActivationResend(
            ResendActivationRequest request
    ) {
        String tokenHash;

        try {
            tokenHash = tokenCodec.hash(
                    request.getToken()
            );
        } catch (IllegalArgumentException exception) {
            return;
        }

        PlatformUserActivationToken existingToken =
                activationTokenRepository
                        .findByTokenHash(tokenHash)
                        .orElse(null);

        /*
         * Finish silently so callers cannot use this endpoint
         * to discover platform employee accounts.
         */
        if (existingToken == null) {
            return;
        }

        PlatformUser platformUser = platformUserRepository
                .findById(
                        existingToken.getPlatformUserId()
                )
                .orElse(null);

        if (platformUser == null) {
            return;
        }

        if (platformUser.getStatus()
                != PlatformUserStatus.PENDING_ACTIVATION) {
            return;
        }

        eventPublisher.publishEvent(
                new PlatformUserActivationRequestedEvent(
                        platformUser.getId()
                )
        );

        auditService.record(
                platformUser.getId(),
                SecurityAuditAction.PLATFORM_ACTIVATION_RESEND,
                SecurityAuditOutcome.SUCCESS,
                "PLATFORM_USER",
                platformUser.getId(),
                Map.of("requestedBy", "token_holder")
        );
    }

    private InvalidActivationTokenException invalidToken() {
        return new InvalidActivationTokenException(
                INVALID_TOKEN_MESSAGE
        );
    }
}
