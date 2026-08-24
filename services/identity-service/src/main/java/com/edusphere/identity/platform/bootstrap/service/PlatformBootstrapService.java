package com.edusphere.identity.platform.bootstrap.service;

import com.edusphere.identity.platform.auth.activation.event.PlatformUserActivationRequestedEvent;
import com.edusphere.identity.platform.bootstrap.config.PlatformBootstrapProperties;
import com.edusphere.identity.platform.user.entity.PlatformUser;
import com.edusphere.identity.platform.user.enums.PlatformRole;
import com.edusphere.identity.platform.user.repository.PlatformUserRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.edusphere.identity.platform.bootstrap.PlatformBootstrapResult;
import com.edusphere.identity.platform.user.enums.PlatformUserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
public class PlatformBootstrapService {

    private final PlatformBootstrapProperties bootstrapProperties;
    private final PlatformUserRepository platformUserRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PlatformBootstrapService(
            PlatformBootstrapProperties bootstrapProperties,
            PlatformUserRepository platformUserRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.bootstrapProperties = bootstrapProperties;
        this.platformUserRepository = platformUserRepository;
        this.eventPublisher = eventPublisher;
    }

    private String normalizeRequiredIdentityValue(
            String value,
            String errorMessage
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(errorMessage);
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public PlatformBootstrapResult bootstrapFirstSuperAdmin() {
        if (!bootstrapProperties.isEnabled()) {
            return PlatformBootstrapResult.SKIPPED;
        }

        String username = normalizeRequiredIdentityValue(
                bootstrapProperties.getUsername(),
                "Platform bootstrap username is required"
        );

        String email = normalizeRequiredIdentityValue(
                bootstrapProperties.getEmail(),
                "Platform bootstrap email is required"
        );

        String firstName = normalizeRequiredText(
                bootstrapProperties.getFirstName(),
                "Platform bootstrap first name is required"
        );

        long platformUserCount =
                platformUserRepository.count();

        if (platformUserCount > 0) {
            return handleExistingPlatformUser(
                    platformUserCount,
                    username,
                    email
            );
        }

        PlatformUser superAdmin = new PlatformUser(
                username,
                firstName,
                email,
                Set.of(PlatformRole.PLATFORM_SUPER_ADMIN)
        );

        superAdmin.setMiddleName(
                normalizeOptionalText(
                        bootstrapProperties.getMiddleName()
                )
        );

        superAdmin.setLastName(
                normalizeOptionalText(
                        bootstrapProperties.getLastName()
                )
        );

        superAdmin.setPhone(
                normalizeOptionalText(
                        bootstrapProperties.getPhone()
                )
        );

        PlatformUser savedSuperAdmin =
                platformUserRepository.save(superAdmin);

        eventPublisher.publishEvent(
                new PlatformUserActivationRequestedEvent(
                        savedSuperAdmin.getId()
                )
        );

        return PlatformBootstrapResult.CREATED;
    }

    private PlatformBootstrapResult handleExistingPlatformUser(
            long platformUserCount,
            String configuredUsername,
            String configuredEmail
    ) {
        /*
         * Recovery is allowed only when exactly one platform user exists.
         * Once employees have been added, bootstrap can never be used
         * as a general activation-resend mechanism.
         */
        if (platformUserCount != 1) {
            return PlatformBootstrapResult.SKIPPED;
        }

        PlatformUser existingUser = platformUserRepository
                .findByUsernameIgnoreCase(configuredUsername)
                .orElse(null);

        if (existingUser == null) {
            return PlatformBootstrapResult.SKIPPED;
        }

        boolean sameEmail =
                existingUser.getEmail() != null
                        && existingUser.getEmail()
                        .equalsIgnoreCase(configuredEmail);

        boolean pendingActivation =
                existingUser.getStatus()
                        == PlatformUserStatus.PENDING_ACTIVATION;

        boolean onlySuperAdminRole =
                existingUser.getRoles().equals(
                        Set.of(PlatformRole.PLATFORM_SUPER_ADMIN)
                );

        if (!sameEmail
                || !pendingActivation
                || !onlySuperAdminRole) {
            return PlatformBootstrapResult.SKIPPED;
        }

        eventPublisher.publishEvent(
                new PlatformUserActivationRequestedEvent(
                        existingUser.getId()
                )
        );

        return PlatformBootstrapResult.ACTIVATION_REISSUED;
    }

    private String normalizeRequiredText(
            String value,
            String errorMessage
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(errorMessage);
        }

        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}