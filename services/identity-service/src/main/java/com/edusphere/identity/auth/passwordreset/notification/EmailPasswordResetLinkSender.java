package com.edusphere.identity.auth.passwordreset.notification;

import com.edusphere.identity.auth.passwordreset.config.PasswordResetLinkProperties;
import com.edusphere.identity.user.entity.User;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class EmailPasswordResetLinkSender
        implements PasswordResetLinkSender {

    private final JavaMailSender mailSender;
    private final PasswordResetLinkProperties linkProperties;

    public EmailPasswordResetLinkSender(
            JavaMailSender mailSender,
            PasswordResetLinkProperties linkProperties
    ) {
        this.mailSender = mailSender;
        this.linkProperties = linkProperties;
    }

    @Override
    public void sendPasswordResetLink(User user, String rawToken) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException(
                    "User email is required to send a password reset link"
            );
        }

        String resetUrl = UriComponentsBuilder
                .fromUriString(linkProperties.getBaseUrl())
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(linkProperties.getFromAddress());
        message.setTo(user.getEmail());
        message.setSubject("Reset your Dawnrise password");
        message.setText(buildEmailBody(user, resetUrl));

        mailSender.send(message);
    }

    private String buildEmailBody(User user, String resetUrl) {
        return """
                Hello %s,

                We received a request to reset your Dawnrise password.

                Username: %s

                Use the link below to set a new password:

                %s

                This password reset link is single-use and will expire automatically.
                If you did not request this change, you can ignore this email.

                Dawnrise
                From Chaos to Clarity. Powered by Dawnrise.
                """.formatted(
                user.getFirstName(),
                user.getUsername(),
                resetUrl
        );
    }
}
