package com.edusphere.identity.auth.passwordreset.service;

import com.edusphere.identity.auth.activation.exception.PasswordMismatchException;
import com.edusphere.identity.auth.exception.PasswordChangeNotAllowedException;
import com.edusphere.identity.auth.activation.security.ActivationTokenCodec;
import com.edusphere.identity.auth.passwordreset.config.PasswordResetTokenProperties;
import com.edusphere.identity.auth.passwordreset.dto.CompletePasswordResetRequest;
import com.edusphere.identity.auth.passwordreset.dto.PasswordResetRequest;
import com.edusphere.identity.auth.passwordreset.entity.UserPasswordResetToken;
import com.edusphere.identity.auth.passwordreset.event.UserPasswordResetRequestedEvent;
import com.edusphere.identity.auth.passwordreset.exception.InvalidPasswordResetTokenException;
import com.edusphere.identity.auth.passwordreset.repository.UserPasswordResetTokenRepository;
import com.edusphere.identity.organization.repository.OrganizationRepository;
import org.springframework.transaction.annotation.Propagation;
import com.edusphere.identity.auth.refreshtoken.service.RefreshTokenService;
import com.edusphere.identity.common.exception.ResourceNotFoundException;
import com.edusphere.identity.user.entity.User;
import com.edusphere.identity.user.enums.UserStatus;
import com.edusphere.identity.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final String INVALID_TOKEN_MESSAGE =
            "The password reset token is invalid or expired";

    private final UserPasswordResetTokenRepository resetTokenRepository;
    private final UserRepository userRepository;
    private final ActivationTokenCodec tokenCodec;
    private final PasswordResetTokenProperties tokenProperties;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final RefreshTokenService refreshTokenService;
    private final OrganizationRepository organizationRepository;

    public PasswordResetServiceImpl(
            UserPasswordResetTokenRepository resetTokenRepository,
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            ActivationTokenCodec tokenCodec,
            PasswordResetTokenProperties tokenProperties,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            RefreshTokenService refreshTokenService
    ) {
        this.resetTokenRepository = resetTokenRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.tokenCodec = tokenCodec;
        this.tokenProperties = tokenProperties;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    @Transactional
    public void requestPasswordReset(
            PasswordResetRequest request
    ) {
        // Always finish silently so callers cannot discover schools or accounts.
        organizationRepository
                .findBySchoolCodeIgnoreCase(
                        request.getSchoolCode().trim()
                )
                .flatMap(organization ->
                        userRepository
                                .findByOrganizationIdAndEmailIgnoreCase(
                                        organization.getId(),
                                        request.getEmail().trim()
                                )
                )
                .filter(user ->
                        user.getStatus() == UserStatus.ACTIVE
                )
                .ifPresent(user ->
                        eventPublisher.publishEvent(
                                new UserPasswordResetRequestedEvent(
                                        user.getId()
                                )
                        )
                );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generatePasswordResetToken(Long userId){
        // Token generation is event-driven after a safe public request.
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found"
                ));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalidToken();
        }

        OffsetDateTime currentTime = OffsetDateTime.now();
        OffsetDateTime requestWindowStart =
                currentTime.minus(tokenProperties.getRequestWindow());

        // A completed reset starts a fresh email allowance. Without this,
        // emails requested before a successful reset can prevent the user
        // from recovering the account again for the rest of the window.
        OffsetDateTime emailLimitWindowStart =
                user.getPasswordChangedAt() != null
                        && user.getPasswordChangedAt()
                        .isAfter(requestWindowStart)
                        ? user.getPasswordChangedAt()
                        : requestWindowStart;

        long emailsGenerated =
                resetTokenRepository.countByUserIdAndCreatedAtAfter(
                        userId,
                        emailLimitWindowStart
                );

        if (emailsGenerated
                >= tokenProperties.getMaxEmailsPerWindow()) {
            // Throttle email generation to reduce abuse and inbox flooding.
            throw new IllegalStateException(
                    "Password reset email rate limit exceeded"
            );
        }

        List<UserPasswordResetToken> previousTokens =
                resetTokenRepository
                        .findAllByUserIdAndUsedAtIsNullAndRevokedAtIsNull(
                                userId
                        );

        // Only the newest reset link remains usable.
        previousTokens.forEach(token -> token.revoke(currentTime));

        String rawToken = tokenCodec.generateRawToken();
        String tokenHash = tokenCodec.hash(rawToken);

        UserPasswordResetToken resetToken =
                new UserPasswordResetToken(
                        userId,
                        tokenHash,
                        currentTime.plus(tokenProperties.getExpiration())
                );

        resetTokenRepository.save(resetToken);

        return rawToken;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPasswordResetTokenValid(String rawToken) {
        // Validation endpoint is read-only and never consumes the token.
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        String tokenHash;

        try {
            tokenHash = tokenCodec.hash(rawToken);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        return resetTokenRepository
                .findByTokenHash(tokenHash)
                .filter(token -> token.isValidAt(OffsetDateTime.now()))
                .flatMap(token -> userRepository.findById(token.getUserId()))
                .map(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElse(false);
    }

    @Override
    @Transactional
    public void completePasswordReset(
            CompletePasswordResetRequest request
    ) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException(
                    "Password and confirmation do not match"
            );
        }

        String tokenHash = tokenCodec.hash(request.getToken());

        UserPasswordResetToken resetToken =
                resetTokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(this::invalidToken);

        OffsetDateTime currentTime = OffsetDateTime.now();

        if (!resetToken.isValidAt(currentTime)) {
            // Used, revoked, and expired tokens are all treated the same.
            throw invalidToken();
        }

        User user = userRepository
                .findById(resetToken.getUserId())
                .orElseThrow(this::invalidToken);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalidToken();
        }

        if (user.getPasswordHash() != null
                && passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash()
        )) {
            throw new PasswordChangeNotAllowedException(
                    "New password must be different from the current password"
            );
        }

        // Emergency reset bypasses ordinary cooldown but burns the token.
        user.resetPassword(passwordEncoder.encode(request.getPassword()));
        resetToken.markUsed(currentTime);

        // Reset after email proof invalidates every existing session.
        refreshTokenService.revokeAllForUser(user.getId());
    }

    private InvalidPasswordResetTokenException invalidToken() {
        return new InvalidPasswordResetTokenException(
                INVALID_TOKEN_MESSAGE
        );
    }
}
