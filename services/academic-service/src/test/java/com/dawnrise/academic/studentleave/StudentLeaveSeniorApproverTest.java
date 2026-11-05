package com.dawnrise.academic.studentleave;

import com.dawnrise.academic.notificationdelivery.IdentityNotificationClient;
import com.dawnrise.academic.results.integration.identity.IdentityGuardianAccessClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentLeaveSeniorApproverTest {
    private NamedParameterJdbcTemplate db;
    private IdentityNotificationClient identity;
    private IdentityGuardianAccessClient guardians;
    private StudentLeaveService service;

    @BeforeEach
    void setUp() {
        db = mock(NamedParameterJdbcTemplate.class);
        identity = mock(IdentityNotificationClient.class);
        guardians = mock(IdentityGuardianAccessClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<NamedParameterJdbcTemplate> database = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<IdentityNotificationClient> identities = mock(ObjectProvider.class);
        when(database.getIfAvailable()).thenReturn(db);
        when(identities.getIfAvailable()).thenReturn(identity);
        service = new StudentLeaveService(database, guardians, identities);
    }

    @Test
    void usesAcademicHeadBeforeConfiguredPrincipal() {
        doReturn(List.of(new StudentLeaveService.Route("ACADEMIC_HEAD", 40L),
                new StudentLeaveService.Route("PRINCIPAL", 50L)))
                .when(db).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));

        assertEquals(new StudentLeaveService.Route("ACADEMIC_HEAD", 40L),
                service.seniorApprover(1, 10, 2, 3, 4, 20));
    }

    @Test
    void routesToActivePrincipalGroupWhenNoHeadMappingExists() {
        doReturn(List.of()).when(db).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
        when(identity.resolveRoles(1, Set.of("PRINCIPAL"))).thenReturn(Set.of(50L));

        assertEquals(new StudentLeaveService.Route("PRINCIPAL", null),
                service.seniorApprover(1, 10, 2, 3, 4, 20));
        verify(identity).resolveRoles(1, Set.of("PRINCIPAL"));
    }

    @Test
    void usesConfiguredPrincipalWhenHeadIsUnavailable() {
        doReturn(List.of(new StudentLeaveService.Route("ACADEMIC_HEAD", 20L),
                new StudentLeaveService.Route("PRINCIPAL", 50L)))
                .when(db).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));

        assertEquals(new StudentLeaveService.Route("PRINCIPAL", 50L),
                service.seniorApprover(1, 10, 2, 3, 4, 20));
    }

    @Test
    void multipleActivePrincipalsCanShareFallbackStage() {
        doReturn(List.of(new StudentLeaveService.Route("ACADEMIC_HEAD", 20L)))
                .when(db).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
        when(identity.resolveRoles(1, Set.of("PRINCIPAL"))).thenReturn(Set.of(50L, 51L));

        assertEquals(new StudentLeaveService.Route("PRINCIPAL", null),
                service.seniorApprover(1, 10, 2, 3, 4, 20));
    }

    @Test
    void noActivePrincipalReturnsSpecificValidationError() {
        doReturn(List.of()).when(db).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
        when(identity.resolveRoles(1, Set.of("PRINCIPAL"))).thenReturn(Set.of());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.seniorApprover(1, 10, 2, 3, 4, 20));
        assertEquals(422, error.getStatusCode().value());
        assertEquals(true, error.getReason().contains("principal account and role"));
    }

    @Test
    void guardianWithPrincipalRoleCannotApproveOwnChild() {
        doReturn(List.of()).when(db).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
        when(identity.resolveRoles(1, Set.of("PRINCIPAL"))).thenReturn(Set.of(50L));
        when(guardians.hasActiveRelationship(1, 50, 10)).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.seniorApprover(1, 10, 2, 3, 4, 20));
        assertEquals(422, error.getStatusCode().value());
    }
}
