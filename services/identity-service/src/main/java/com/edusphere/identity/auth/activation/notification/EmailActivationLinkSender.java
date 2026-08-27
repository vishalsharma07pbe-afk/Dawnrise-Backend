package com.edusphere.identity.auth.activation.notification;

import com.edusphere.identity.auth.activation.config.ActivationLinkProperties;
import com.edusphere.identity.user.entity.User;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class EmailActivationLinkSender implements ActivationLinkSender {

    private final JavaMailSender mailSender;
    private final ActivationLinkProperties linkProperties;

    public EmailActivationLinkSender(
            JavaMailSender mailSender,
            ActivationLinkProperties linkProperties
    ) {
        this.mailSender = mailSender;
        this.linkProperties = linkProperties;
    }

    @Override
    public void sendActivationLink(
            User user,
            String rawToken
    ) {
        if (user.getEmail() == null
                || user.getEmail().isBlank()) {
            throw new IllegalStateException(
                    "User email is required to send an activation link"
            );
        }

        String activationUrl = UriComponentsBuilder
                .fromUriString(linkProperties.getBaseUrl())
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(linkProperties.getFromAddress());
        message.setTo(user.getEmail());
        message.setSubject("Activate your Dawnrise account");
        message.setText(buildEmailBody(user, activationUrl));

        mailSender.send(message);
    }

    private String buildEmailBody(
            User user,
            String activationUrl
    ) {
        return """
                Hello %s,

                Your Dawnrise account has been created.

                Username: %s

                Use the link below to create your password and activate your account:

                %s

                This activation link is single-use and will expire automatically.
                If you were not expecting this invitation, you can ignore this email.

                Dawnrise
                From Chaos to Clarity. Powered by Dawnrise.
                """.formatted(
                user.getFirstName(),
                user.getUsername(),
                activationUrl
        );
    }
}
