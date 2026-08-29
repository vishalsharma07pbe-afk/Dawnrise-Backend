package com.dawnrise.identity.platform.auth.passwordreset.service;

import com.dawnrise.identity.auth.activation.exception.PasswordMismatchException;
import com.dawnrise.identity.auth.activation.security.ActivationTokenCodec;
import com.dawnrise.identity.auth.exception.PasswordChangeNotAllowedException;
import com.dawnrise.identity.auth.passwordreset.config.PasswordResetLinkProperties;
import com.dawnrise.identity.auth.passwordreset.config.PasswordResetTokenProperties;
import com.dawnrise.identity.auth.passwordreset.dto.CompletePasswordResetRequest;
import com.dawnrise.identity.auth.passwordreset.exception.InvalidPasswordResetTokenException;
import com.dawnrise.identity.platform.auth.passwordreset.dto.PlatformPasswordResetRequest;
import com.dawnrise.identity.platform.auth.passwordreset.entity.PlatformUserPasswordResetToken;
import com.dawnrise.identity.platform.auth.passwordreset.repository.PlatformUserPasswordResetTokenRepository;
import com.dawnrise.identity.platform.auth.refreshtoken.service.PlatformRefreshTokenService;
import com.dawnrise.identity.platform.user.entity.PlatformUser;
import com.dawnrise.identity.platform.user.enums.PlatformUserStatus;
import com.dawnrise.identity.platform.user.repository.PlatformUserRepository;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.OffsetDateTime;

@Service
public class PlatformPasswordResetServiceImpl implements PlatformPasswordResetService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformPasswordResetServiceImpl.class);
    private static final String INVALID = "The platform password reset token is invalid or expired";
    private final PlatformUserPasswordResetTokenRepository tokens;
    private final PlatformUserRepository users;
    private final ActivationTokenCodec codec;
    private final PasswordResetTokenProperties properties;
    private final PasswordResetLinkProperties links;
    private final PasswordEncoder encoder;
    private final PlatformRefreshTokenService refreshTokens;
    private final JavaMailSender mailSender;

    public PlatformPasswordResetServiceImpl(PlatformUserPasswordResetTokenRepository tokens,
            PlatformUserRepository users, ActivationTokenCodec codec, PasswordResetTokenProperties properties,
            PasswordResetLinkProperties links, PasswordEncoder encoder,
            PlatformRefreshTokenService refreshTokens, JavaMailSender mailSender) {
        this.tokens = tokens; this.users = users; this.codec = codec; this.properties = properties;
        this.links = links; this.encoder = encoder; this.refreshTokens = refreshTokens; this.mailSender = mailSender;
    }

    @Override @Transactional
    public void requestReset(PlatformPasswordResetRequest request) {
        users.findByUsernameIgnoreCase(request.getUsername().trim())
                .filter(user -> user.getStatus() == PlatformUserStatus.ACTIVE)
                .ifPresent(this::createAndSend);
    }

    private void createAndSend(PlatformUser user) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime window = now.minus(properties.getRequestWindow());
        if (tokens.countByPlatformUserIdAndCreatedAtAfter(user.getId(), window) >= properties.getMaxEmailsPerWindow()) return;
        tokens.findAllByPlatformUserIdAndUsedAtIsNullAndRevokedAtIsNull(user.getId()).forEach(token -> token.revoke(now));
        String rawToken = codec.generateRawToken();
        tokens.save(new PlatformUserPasswordResetToken(user.getId(), codec.hash(rawToken), now.plus(properties.getExpiration())));
        String url = UriComponentsBuilder.fromUriString(links.getPlatformBaseUrl()).queryParam("token", rawToken).build().encode().toUriString();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(links.getFromAddress()); message.setTo(user.getEmail());
        message.setSubject("Reset your Dawnrise platform password");
        message.setText("Hello " + user.getFirstName() + ",\n\nUse this single-use link to reset your Dawnrise platform password:\n\n" + url + "\n\nIf you did not request this, ignore this email.\n\nDawnrise");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            // Preserve the same public response for matching and non-matching usernames.
            LOGGER.error("Failed to send platform password reset email for user ID {}", user.getId(), exception);
        }
    }

    @Override @Transactional(readOnly = true)
    public boolean isTokenValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return false;
        try {
            return tokens.findByTokenHash(codec.hash(rawToken)).filter(token -> token.isValidAt(OffsetDateTime.now()))
                    .flatMap(token -> users.findById(token.getPlatformUserId()))
                    .map(user -> user.getStatus() == PlatformUserStatus.ACTIVE).orElse(false);
        } catch (IllegalArgumentException exception) { return false; }
    }

    @Override @Transactional
    public void completeReset(CompletePasswordResetRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) throw new PasswordMismatchException("Password and confirmation do not match");
        PlatformUserPasswordResetToken token = tokens.findByTokenHash(codec.hash(request.getToken())).orElseThrow(this::invalid);
        OffsetDateTime now = OffsetDateTime.now();
        if (!token.isValidAt(now)) throw invalid();
        PlatformUser user = users.findById(token.getPlatformUserId()).filter(value -> value.getStatus() == PlatformUserStatus.ACTIVE).orElseThrow(this::invalid);
        if (user.getPasswordHash() != null && encoder.matches(request.getPassword(), user.getPasswordHash()))
            throw new PasswordChangeNotAllowedException("New password must be different from the current password");
        user.resetPassword(encoder.encode(request.getPassword()));
        token.markUsed(now);
        refreshTokens.revokeAllForPlatformUser(user.getId());
    }
    private InvalidPasswordResetTokenException invalid() { return new InvalidPasswordResetTokenException(INVALID); }
}
