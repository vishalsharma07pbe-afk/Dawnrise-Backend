package com.dawnrise.identity.auth.activation.notification;

import com.dawnrise.identity.auth.activation.config.ActivationLinkProperties;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
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
class EmailActivationLinkSenderTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailActivationLinkSender sender;

    @BeforeEach
    void setUp() {
        ActivationLinkProperties linkProperties =
                new ActivationLinkProperties();
        linkProperties.setBaseUrl(
                "https://app.dawnrise.com/activate"
        );
        linkProperties.setFromAddress("no-reply@dawnrise.com");

        sender = new EmailActivationLinkSender(
                mailSender,
                linkProperties
        );
    }

    @Test
    void sendActivationLink_includesUsernameInEmailBody() {
        User user = new User(
                1L,
                "teacher01",
                "Rahul",
                Set.of(UserRole.TEACHER)
        );
        user.setEmail("teacher@dawnrise.com");

        sender.sendActivationLink(user, "raw-token");

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();
        assertEquals("no-reply@dawnrise.com", message.getFrom());
        assertEquals(
                "teacher@dawnrise.com",
                message.getTo()[0]
        );
        assertEquals(
                "Activate your Dawnrise account",
                message.getSubject()
        );
        assertTrue(message.getText().contains("Hello Rahul,"));
        assertTrue(message.getText().contains("Username: teacher01"));
        assertTrue(message.getText().contains(
                "https://app.dawnrise.com/activate?token=raw-token"
        ));
    }
}
