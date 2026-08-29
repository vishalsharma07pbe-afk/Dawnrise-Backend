package com.dawnrise.identity.auth.passwordreset.event;

import com.dawnrise.identity.auth.passwordreset.notification.PasswordResetLinkSender;
import com.dawnrise.identity.auth.passwordreset.service.PasswordResetService;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UserPasswordResetRequestedListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    UserPasswordResetRequestedListener.class
            );

    private final PasswordResetService passwordResetService;
    private final PasswordResetLinkSender passwordResetLinkSender;
    private final UserRepository userRepository;

    public UserPasswordResetRequestedListener(
            PasswordResetService passwordResetService,
            PasswordResetLinkSender passwordResetLinkSender,
            UserRepository userRepository
    ) {
        this.passwordResetService = passwordResetService;
        this.passwordResetLinkSender = passwordResetLinkSender;
        this.userRepository = userRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserPasswordResetRequested(
            UserPasswordResetRequestedEvent event
    ) {
        try {
            User user = userRepository
                    .findById(event.userId())
                    .orElseThrow(() -> new IllegalStateException(
                            "User not found after password reset request"
                    ));

            String rawToken =
                    passwordResetService.generatePasswordResetToken(
                            user.getId()
                    );

            passwordResetLinkSender.sendPasswordResetLink(
                    user,
                    rawToken
            );
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Failed to create or send password reset link for user ID {}",
                    event.userId(),
                    exception
            );
        }
    }
}
