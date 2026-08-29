package com.dawnrise.identity.securityaudit.service;

import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.securityaudit.dto.SecurityAuditEventResponse;
import com.dawnrise.identity.securityaudit.entity.SecurityAuditEvent;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;
import com.dawnrise.identity.securityaudit.mapper.SecurityAuditEventMapper;
import com.dawnrise.identity.securityaudit.repository.SecurityAuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityAuditServiceImplTest {

    @Mock
    private SecurityAuditEventRepository repository;

    private SecurityAuditServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SecurityAuditServiceImpl(
                repository,
                new SecurityAuditEventMapper()
        );
    }

    @Test
    void record_savesCorrectActorTargetActionAndOutcome() {
        service.record(
                1L,
                10L,
                SecurityAuditAction.USER_CREATE,
                SecurityAuditOutcome.SUCCESS,
                "USER",
                20L,
                Map.of("status", "PENDING_ACTIVATION")
        );

        ArgumentCaptor<SecurityAuditEvent> captor =
                ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(repository).save(captor.capture());

        SecurityAuditEvent event = captor.getValue();
        assertEquals(1L, event.getOrganizationId());
        assertEquals(10L, event.getActorUserId());
        assertEquals(SecurityAuditAction.USER_CREATE, event.getAction());
        assertEquals(SecurityAuditOutcome.SUCCESS, event.getOutcome());
        assertEquals("USER", event.getTargetType());
        assertEquals(20L, event.getTargetId());
        assertEquals("status=PENDING_ACTIVATION", event.getDetails());
    }

    @Test
    void record_removesSecretDetails() {
        service.record(
                1L,
                10L,
                SecurityAuditAction.PASSWORD_CHANGE,
                SecurityAuditOutcome.SUCCESS,
                "USER",
                10L,
                Map.of(
                        "password", "plain",
                        "refreshToken", "secret",
                        "passwordHash", "hash",
                        "sessionsRevoked", true
                )
        );

        ArgumentCaptor<SecurityAuditEvent> captor =
                ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(repository).save(captor.capture());

        String details = captor.getValue().getDetails();
        assertTrue(details.contains("sessionsRevoked=true"));
        assertFalse(details.contains("plain"));
        assertFalse(details.contains("secret"));
        assertFalse(details.contains("hash"));
    }

    @Test
    void getEvents_usesTenantScopedRepositoryAndPagination() {
        SecurityAuditEvent event = new SecurityAuditEvent(
                1L,
                10L,
                SecurityAuditAction.LOGIN_SUCCESS,
                SecurityAuditOutcome.SUCCESS,
                "USER",
                10L,
                null,
                null,
                null
        );
        PageRequest pageable = PageRequest.of(1, 5);

        when(repository.findAllByOrganizationId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(event), pageable, 6));

        PageResponse<SecurityAuditEventResponse> response =
                service.getEvents(1L, pageable);

        assertEquals(1, response.pageNumber());
        assertEquals(5, response.pageSize());
        assertEquals(6, response.totalElements());
        assertEquals(1, response.content().size());
        verify(repository).findAllByOrganizationId(1L, pageable);
    }

    @Test
    void serviceDoesNotExposeUpdateOrDeleteMethods() {
        for (Method method : SecurityAuditService.class.getMethods()) {
            assertFalse(method.getName().startsWith("update"));
            assertFalse(method.getName().startsWith("delete"));
            assertFalse(method.getName().startsWith("remove"));
        }
    }
}
