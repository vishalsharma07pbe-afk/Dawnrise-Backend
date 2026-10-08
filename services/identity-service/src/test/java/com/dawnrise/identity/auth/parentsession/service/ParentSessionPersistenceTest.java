package com.dawnrise.identity.auth.parentsession.service;

import com.dawnrise.identity.auth.parentsession.config.ParentSessionProperties;
import com.dawnrise.identity.auth.parentsession.entity.ParentSession;
import com.dawnrise.identity.auth.parentsession.exception.ParentSessionLockedException;
import com.dawnrise.identity.auth.parentsession.repository.ParentSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ParentSessionService.class, ParentSessionPersistenceTest.Configuration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ParentSessionPersistenceTest {

    @Autowired
    private ParentSessionRepository sessions;

    @Autowired
    private ParentSessionService service;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @Test
    void idleRequestDurablyStoresTheLock() {
        UUID id = expiredSession();

        assertThrows(
                ParentSessionLockedException.class,
                () -> service.validateAndTouch(id, 7L)
        );

        assertTrue(sessions.findById(id).orElseThrow().isLocked());
    }

    @Test
    void concurrentIdleRequestsLeaveOneDurableLockedSession() throws Exception {
        UUID id = expiredSession();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Class<?>> first = executor.submit(() -> validateAfter(ready, start, id));
            Future<Class<?>> second = executor.submit(() -> validateAfter(ready, start, id));
            ready.await();
            start.countDown();

            assertTrue(ParentSessionLockedException.class.isAssignableFrom(first.get()));
            assertTrue(ParentSessionLockedException.class.isAssignableFrom(second.get()));
        }

        assertTrue(sessions.findById(id).orElseThrow().isLocked());
    }

    private Class<?> validateAfter(
            CountDownLatch ready,
            CountDownLatch start,
            UUID id
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            service.validateAndTouch(id, 7L);
            return Void.class;
        } catch (RuntimeException exception) {
            return exception.getClass();
        }
    }

    private UUID expiredSession() {
        UUID id = UUID.randomUUID();
        sessions.saveAndFlush(new ParentSession(
                id,
                7L,
                OffsetDateTime.now().minusMinutes(20)
        ));
        return id;
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        ParentSessionProperties parentSessionProperties() {
            ParentSessionProperties properties = new ParentSessionProperties();
            properties.setInactivityTimeout(Duration.ofMinutes(15));
            properties.setRecentAuthentication(Duration.ofMinutes(10));
            return properties;
        }
    }
}
