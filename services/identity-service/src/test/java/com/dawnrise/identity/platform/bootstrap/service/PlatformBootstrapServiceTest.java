package com.dawnrise.identity.platform.bootstrap.service;

import com.dawnrise.identity.platform.auth.activation.event.PlatformUserActivationRequestedEvent;
import com.dawnrise.identity.platform.bootstrap.PlatformBootstrapResult;
import com.dawnrise.identity.platform.bootstrap.config.PlatformBootstrapProperties;
import com.dawnrise.identity.platform.user.entity.PlatformUser;
import com.dawnrise.identity.platform.user.enums.PlatformRole;
import com.dawnrise.identity.platform.user.enums.PlatformUserStatus;
import com.dawnrise.identity.platform.user.repository.PlatformUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformBootstrapServiceTest {

    @Mock
    private PlatformUserRepository platformUserRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PlatformBootstrapProperties properties;
    private PlatformBootstrapService service;

    @BeforeEach
    void setUp() {
        properties = new PlatformBootstrapProperties();
        properties.setUsername("root.admin");
        properties.setFirstName("Root");
        properties.setEmail("root@dawnrise.in");

        service = new PlatformBootstrapService(
                properties,
                platformUserRepository,
                eventPublisher
        );
    }

    @Test
    void bootstrap_whenDisabled_skipsWithoutRepositoryAccess() {
        properties.setEnabled(false);

        assertEquals(
                PlatformBootstrapResult.SKIPPED,
                service.bootstrapFirstSuperAdmin()
        );
        verifyNoInteractions(platformUserRepository, eventPublisher);
    }

    @Test
    void bootstrap_whenNoUsers_createsPendingSuperAdminAndPublishesEvent() {
        properties.setEnabled(true);

        when(platformUserRepository.count()).thenReturn(0L);
        when(platformUserRepository.save(any(PlatformUser.class)))
                .thenAnswer(invocation -> {
                    PlatformUser user = invocation.getArgument(0);
                    ReflectionTestUtils.setField(user, "id", 1L);
                    return user;
                });

        assertEquals(
                PlatformBootstrapResult.CREATED,
                service.bootstrapFirstSuperAdmin()
        );

        ArgumentCaptor<PlatformUser> userCaptor =
                ArgumentCaptor.forClass(PlatformUser.class);
        verify(platformUserRepository).save(userCaptor.capture());
        assertEquals(
                PlatformUserStatus.PENDING_ACTIVATION,
                userCaptor.getValue().getStatus()
        );
        assertEquals(
                Set.of(PlatformRole.PLATFORM_SUPER_ADMIN),
                userCaptor.getValue().getRoles()
        );
        verify(eventPublisher).publishEvent(
                new PlatformUserActivationRequestedEvent(1L)
        );
    }

    @Test
    void bootstrap_whenSolePendingMatchingSuperAdmin_reissuesActivation() {
        properties.setEnabled(true);
        PlatformUser existingUser = pendingSuperAdmin();

        when(platformUserRepository.count()).thenReturn(1L);
        when(platformUserRepository.findByUsernameIgnoreCase("root.admin"))
                .thenReturn(Optional.of(existingUser));

        assertEquals(
                PlatformBootstrapResult.ACTIVATION_REISSUED,
                service.bootstrapFirstSuperAdmin()
        );
        verify(eventPublisher).publishEvent(
                new PlatformUserActivationRequestedEvent(1L)
        );
        verify(platformUserRepository, never()).save(any());
    }

    @Test
    void bootstrap_whenExistingUserActive_skipsRecovery() {
        properties.setEnabled(true);
        PlatformUser existingUser = pendingSuperAdmin();
        existingUser.setStatus(PlatformUserStatus.ACTIVE);

        when(platformUserRepository.count()).thenReturn(1L);
        when(platformUserRepository.findByUsernameIgnoreCase("root.admin"))
                .thenReturn(Optional.of(existingUser));

        assertEquals(
                PlatformBootstrapResult.SKIPPED,
                service.bootstrapFirstSuperAdmin()
        );
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void bootstrap_whenAdditionalUsersExist_skipsRecovery() {
        properties.setEnabled(true);

        when(platformUserRepository.count()).thenReturn(2L);

        assertEquals(
                PlatformBootstrapResult.SKIPPED,
                service.bootstrapFirstSuperAdmin()
        );
        verifyNoInteractions(eventPublisher);
    }

    private static PlatformUser pendingSuperAdmin() {
        PlatformUser user = new PlatformUser(
                "root.admin",
                "Root",
                "root@dawnrise.in",
                Set.of(PlatformRole.PLATFORM_SUPER_ADMIN)
        );
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
