package com.dawnrise.identity.platform.auth.activation.event;

import com.dawnrise.identity.platform.auth.activation.notification.PlatformActivationLinkSender;
import com.dawnrise.identity.platform.auth.activation.service.PlatformAccountActivationService;
import com.dawnrise.identity.platform.user.entity.PlatformUser;
import com.dawnrise.identity.platform.user.repository.PlatformUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PlatformUserActivationRequestedListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    PlatformUserActivationRequestedListener.class
            );

    private final PlatformAccountActivationService activationService;
    private final PlatformActivationLinkSender activationLinkSender;
    private final PlatformUserRepository platformUserRepository;

    public PlatformUserActivationRequestedListener(
            PlatformAccountActivationService activationService,
            PlatformActivationLinkSender activationLinkSender,
            PlatformUserRepository platformUserRepository
    ) {
        this.activationService = activationService;
        this.activationLinkSender = activationLinkSender;
        this.platformUserRepository = platformUserRepository;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handlePlatformUserActivationRequested(
            PlatformUserActivationRequestedEvent event
    ) {
        try {
            PlatformUser platformUser = platformUserRepository
                    .findById(event.platformUserId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Platform user not found after account creation"
                    ));

            String rawToken =
                    activationService.generateActivationToken(
                            platformUser.getId()
                    );

            activationLinkSender.sendActivationLink(
                    platformUser,
                    rawToken
            );
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Failed to create or send platform activation link "
                            + "for platform user ID {}",
                    event.platformUserId(),
                    exception
            );
        }
    }
}