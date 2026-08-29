package com.dawnrise.identity.auth.passwordreset.notification;

import com.dawnrise.identity.auth.passwordreset.config.PasswordResetLinkProperties;
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
class EmailPasswordResetLinkSenderTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailPasswordResetLinkSender sender;

    @BeforeEach
    void setUp() {
        PasswordResetLinkProperties linkProperties =
                new PasswordResetLinkProperties();
        linkProperties.setBaseUrl(
                "https://app.dawnrise.com/reset-password"
        );
        linkProperties.setFromAddress("no-reply@dawnrise.com");

        sender = new EmailPasswordResetLinkSender(
                mailSender,
                linkProperties
        );
    }

    @Test
    void sendPasswordResetLink_includesUsernameInEmailBody() {
        User user = new User(
                1L,
                "teacher01",
                "Rahul",
                Set.of(UserRole.TEACHER)
        );
        user.setEmail("teacher@dawnrise.com");

        sender.sendPasswordResetLink(user, "raw-token");

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
                "Reset your Dawnrise password",
                message.getSubject()
        );
        assertTrue(message.getText().contains("Hello Rahul,"));
        assertTrue(message.getText().contains("Username: teacher01"));
        assertTrue(message.getText().contains(
                "https://app.dawnrise.com/reset-password?token=raw-token"
        ));
    }
}
