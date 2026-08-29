package com.dawnrise.identity.platform.auth.activation.notification;

import com.dawnrise.identity.platform.auth.activation.config.PlatformActivationLinkProperties;
import com.dawnrise.identity.platform.user.entity.PlatformUser;
import com.dawnrise.identity.platform.user.enums.PlatformRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailPlatformActivationLinkSenderTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailPlatformActivationLinkSender sender;

    @BeforeEach
    void setUp() {
        PlatformActivationLinkProperties linkProperties =
                new PlatformActivationLinkProperties();
        linkProperties.setBaseUrl(
                "https://admin.dawnrise.com/activate"
        );
        linkProperties.setFromAddress("no-reply@dawnrise.com");

        sender = new EmailPlatformActivationLinkSender(
                mailSender,
                linkProperties
        );
    }

    @Test
    void sendActivationLink_includesUsernameInEmailBody() {
        PlatformUser platformUser = new PlatformUser(
                "employee01",
                "Priya",
                "employee@dawnrise.com",
                Set.of(PlatformRole.PLATFORM_IDENTITY_ADMIN)
        );

        sender.sendActivationLink(platformUser, "raw-token");

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();
        assertEquals("no-reply@dawnrise.com", message.getFrom());
        assertEquals(
                "employee@dawnrise.com",
                message.getTo()[0]
        );
        assertEquals(
                "Activate your Dawnrise employee account",
                message.getSubject()
        );
        assertTrue(message.getText().contains("Hello Priya,"));
        assertTrue(message.getText().contains("Username: employee01"));
        assertTrue(message.getText().contains(
                "https://admin.dawnrise.com/activate?token=raw-token"
        ));
    }
}
