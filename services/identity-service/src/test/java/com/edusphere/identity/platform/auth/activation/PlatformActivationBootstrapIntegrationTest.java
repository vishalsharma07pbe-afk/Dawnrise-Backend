package com.edusphere.identity.platform.auth.activation;

import com.edusphere.identity.auth.activation.security.ActivationTokenCodec;
import com.edusphere.identity.platform.auth.activation.entity.PlatformUserActivationToken;
import com.edusphere.identity.platform.auth.activation.notification.PlatformActivationLinkSender;
import com.edusphere.identity.platform.auth.activation.repository.PlatformUserActivationTokenRepository;
import com.edusphere.identity.platform.auth.activation.service.PlatformAccountActivationService;
import com.edusphere.identity.platform.bootstrap.PlatformBootstrapResult;
import com.edusphere.identity.platform.bootstrap.config.PlatformBootstrapProperties;
import com.edusphere.identity.platform.bootstrap.service.PlatformBootstrapService;
import com.edusphere.identity.platform.user.entity.PlatformUser;
import com.edusphere.identity.platform.user.repository.PlatformUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:platform_activation_bootstrap;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "platform.bootstrap.enabled=false",
        "security.activation-token.expiration=24h",
        "security.activation-token.resend-window=24h",
        "security.activation-token.max-emails-per-window=3"
})
class PlatformActivationBootstrapIntegrationTest {

    @Autowired
    private PlatformBootstrapProperties bootstrapProperties;
    @Autowired
    private PlatformBootstrapService bootstrapService;
    @Autowired
    private PlatformAccountActivationService activationService;
    @Autowired
    private PlatformUserRepository platformUserRepository;
    @Autowired
    private PlatformUserActivationTokenRepository tokenRepository;
    @Autowired
    private ActivationTokenCodec tokenCodec;

    @MockitoBean
    private PlatformActivationLinkSender activationLinkSender;

    private final List<String> sentRawTokens = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        platformUserRepository.deleteAll();
        sentRawTokens.clear();

        bootstrapProperties.setEnabled(true);
        bootstrapProperties.setUsername("root.admin");
        bootstrapProperties.setFirstName("Root");
        bootstrapProperties.setMiddleName(null);
        bootstrapProperties.setLastName(null);
        bootstrapProperties.setEmail("root@dawnrise.in");
        bootstrapProperties.setPhone(null);

        doAnswer(invocation -> {
            String rawToken = invocation.getArgument(1);
            String tokenHash = tokenCodec.hash(rawToken);

            assertTrue(
                    tokenRepository.findByTokenHash(tokenHash)
                            .isPresent(),
                    "Token row must be committed before email is sent"
            );

            sentRawTokens.add(rawToken);
            return null;
        }).when(activationLinkSender)
                .sendActivationLink(
                        any(PlatformUser.class),
                        anyString()
                );
    }

    @Test
    void bootstrapCreatesCommittedTokenBeforeSendingEmail() {
        PlatformBootstrapResult result =
                bootstrapService.bootstrapFirstSuperAdmin();

        assertEquals(PlatformBootstrapResult.CREATED, result);
        assertEquals(1, sentRawTokens.size());
        assertEquals(1, tokenRepository.count());
        assertTrue(
                activationService.isActivationTokenValid(
                        sentRawTokens.getFirst()
                )
        );
    }

    @Test
    void recoveryCreatesReplacementTokenAndRevokesPreviousToken() {
        assertEquals(
                PlatformBootstrapResult.CREATED,
                bootstrapService.bootstrapFirstSuperAdmin()
        );

        String firstRawToken = sentRawTokens.getFirst();

        assertEquals(
                PlatformBootstrapResult.ACTIVATION_REISSUED,
                bootstrapService.bootstrapFirstSuperAdmin()
        );

        assertEquals(2, sentRawTokens.size());
        assertEquals(2, tokenRepository.count());

        String firstHash = tokenCodec.hash(firstRawToken);
        PlatformUserActivationToken firstToken = tokenRepository
                .findByTokenHash(firstHash)
                .orElseThrow();

        assertNotNull(firstToken.getRevokedAt());
        assertFalse(activationService.isActivationTokenValid(firstRawToken));
        assertTrue(
                activationService.isActivationTokenValid(
                        sentRawTokens.get(1)
                )
        );
    }
}
