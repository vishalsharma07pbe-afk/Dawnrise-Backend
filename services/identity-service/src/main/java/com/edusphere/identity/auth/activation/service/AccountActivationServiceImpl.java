package com.edusphere.identity.auth.activation.service;

import com.edusphere.identity.auth.activation.config.ActivationTokenProperties;
import com.edusphere.identity.auth.activation.dto.CompleteAccountActivationRequest;
import com.edusphere.identity.auth.activation.entity.UserActivationToken;
import com.edusphere.identity.auth.activation.exception.InvalidActivationTokenException;
import com.edusphere.identity.auth.activation.exception.PasswordMismatchException;
import com.edusphere.identity.auth.activation.repository.UserActivationTokenRepository;
import com.edusphere.identity.auth.activation.security.ActivationTokenCodec;
import com.edusphere.identity.common.exception.ResourceNotFoundException;
import com.edusphere.identity.user.entity.User;
import com.edusphere.identity.auth.activation.dto.ResendActivationRequest;
import com.edusphere.identity.auth.activation.event.UserActivationRequestedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.edusphere.identity.user.enums.UserStatus;
import com.edusphere.identity.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AccountActivationServiceImpl
        implements AccountActivationService {

    private static final String INVALID_TOKEN_MESSAGE =
            "The activation token is invalid or expired";

    private final UserActivationTokenRepository activationTokenRepository;
    private final UserRepository userRepository;
    private final ActivationTokenCodec tokenCodec;
    private final ActivationTokenProperties tokenProperties;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public AccountActivationServiceImpl(
            UserActivationTokenRepository activationTokenRepository,
            UserRepository userRepository,
            ActivationTokenCodec tokenCodec,
            ActivationTokenProperties tokenProperties,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher
    ) {
        this.activationTokenRepository = activationTokenRepository;
        this.userRepository = userRepository;
        this.tokenCodec = tokenCodec;
        this.tokenProperties = tokenProperties;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateActivationToken(Long userId) {
        // Activation links are only for accounts waiting to set a password.
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found"
                ));

        if (user.getStatus() != UserStatus.PENDING_ACTIVATION) {
            throw invalidToken();
        }

        OffsetDateTime currentTime = OffsetDateTime.now();

        OffsetDateTime resendWindowStart =
                currentTime.minus(
                        tokenProperties.getResendWindow()
                );

        long emailsGenerated =
                activationTokenRepository
                        .countByUserIdAndCreatedAtAfter(
                                userId,
                                resendWindowStart
                        );

        if (emailsGenerated
                >= tokenProperties.getMaxEmailsPerWindow()) {
            // Throttle resend storms and accidental repeated emails.
            throw new IllegalStateException(
                    "Activation email rate limit exceeded"
            );
        }

        List<UserActivationToken> previousTokens = activationTokenRepository
                        .findAllByUserIdAndUsedAtIsNullAndRevokedAtIsNull(
                                userId
                        );

        // Only the newest activation link can be completed.
        previousTokens.forEach(token ->
                token.revoke(currentTime)
        );

        String rawToken = tokenCodec.generateRawToken();
        String tokenHash = tokenCodec.hash(rawToken);

        UserActivationToken activationToken =
                new UserActivationToken(
                        userId,
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
    public boolean isActivationTokenValid(String rawToken) {
        // Frontend validation must not consume or mutate the token.
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
                        userRepository.findById(token.getUserId())
                )
                .map(user ->
                        user.getStatus()
                                == UserStatus.PENDING_ACTIVATION
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

        UserActivationToken activationToken =
                activationTokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(this::invalidToken);

        OffsetDateTime currentTime = OffsetDateTime.now();

        if (!activationToken.isValidAt(currentTime)) {
            // Used, revoked, and expired links get the same public response.
            throw invalidToken();
        }

        User user = userRepository
                .findById(activationToken.getUserId())
                .orElseThrow(this::invalidToken);

        if (user.getStatus() != UserStatus.PENDING_ACTIVATION) {
            throw invalidToken();
        }

        String passwordHash = passwordEncoder.encode(
                request.getPassword()
        );

        // Activation sets the first password and moves the account to ACTIVE.
        user.activate(passwordHash);
        activationToken.markUsed(currentTime);
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

        UserActivationToken existingToken =
                activationTokenRepository
                        .findByTokenHash(tokenHash)
                        .orElse(null);

        // Always finish silently when the token does not exist.
        if (existingToken == null) {
            return;
        }

        User user = userRepository
                .findById(existingToken.getUserId())
                .orElse(null);

        // Do not reveal whether the associated account exists.
        if (user == null) {
            return;
        }

        // Only accounts still waiting for activation can receive another link.
        if (user.getStatus()
                != UserStatus.PENDING_ACTIVATION) {
            return;
        }

        // Generate and send the replacement only after this transaction commits.
        eventPublisher.publishEvent(
                new UserActivationRequestedEvent(
                        user.getId()
                )
        );
    }

    private InvalidActivationTokenException invalidToken() {
        return new InvalidActivationTokenException(
                INVALID_TOKEN_MESSAGE
        );
    }
}
