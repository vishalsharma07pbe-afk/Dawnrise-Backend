package com.dawnrise.identity.auth.activation.event;

import com.dawnrise.identity.auth.activation.notification.ActivationLinkSender;
import com.dawnrise.identity.auth.activation.service.AccountActivationService;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UserActivationRequestedListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    UserActivationRequestedListener.class
            );

    private final AccountActivationService activationService;
    private final ActivationLinkSender activationLinkSender;
    private final UserRepository userRepository;

    public UserActivationRequestedListener(
            AccountActivationService activationService,
            ActivationLinkSender activationLinkSender,
            UserRepository userRepository
    ) {
        this.activationService = activationService;
        this.activationLinkSender = activationLinkSender;
        this.userRepository = userRepository;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleUserActivationRequested(
            UserActivationRequestedEvent event
    ) {
        try {
            User user = userRepository
                    .findById(event.userId())
                    .orElseThrow(() -> new IllegalStateException(
                            "User not found after account creation"
                    ));

            String rawToken =
                    activationService.generateActivationToken(
                            user.getId()
                    );

            activationLinkSender.sendActivationLink(
                    user,
                    rawToken
            );
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Failed to create or send activation link for user ID {}",
                    event.userId(),
                    exception
            );
        }
    }
}