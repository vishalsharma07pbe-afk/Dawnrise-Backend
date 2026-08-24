package com.edusphere.identity.platform.auth.activation.notification;

import com.edusphere.identity.platform.auth.activation.config.PlatformActivationLinkProperties;
import com.edusphere.identity.platform.user.entity.PlatformUser;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class EmailPlatformActivationLinkSender
        implements PlatformActivationLinkSender {

    private final JavaMailSender mailSender;
    private final PlatformActivationLinkProperties linkProperties;

    public EmailPlatformActivationLinkSender(
            JavaMailSender mailSender,
            PlatformActivationLinkProperties linkProperties
    ) {
        this.mailSender = mailSender;
        this.linkProperties = linkProperties;
    }

    @Override
    public void sendActivationLink(
            PlatformUser platformUser,
            String rawToken
    ) {
        if (platformUser.getEmail() == null
                || platformUser.getEmail().isBlank()) {
            throw new IllegalStateException(
                    "Platform user email is required "
                            + "to send an activation link"
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
        message.setTo(platformUser.getEmail());
        message.setSubject(
                "Activate your Dawnrise employee account"
        );
        message.setText(
                buildEmailBody(platformUser, activationUrl)
        );

        mailSender.send(message);
    }

    private String buildEmailBody(
            PlatformUser platformUser,
            String activationUrl
    ) {
        return """
                Hello %s,

                Your Dawnrise employee account has been created.

                Use the link below to create your password and activate your account:

                %s

                This activation link is single-use and will expire automatically.

                Do not share this link with anyone. Dawnrise will never ask you to send your password or activation token by email, phone, or chat.

                If you were not expecting this invitation, ignore this email and contact the Dawnrise identity administrator.

                Dawnrise
                Always one step ahead.
                """.formatted(
                platformUser.getFirstName(),
                activationUrl
        );
    }
}