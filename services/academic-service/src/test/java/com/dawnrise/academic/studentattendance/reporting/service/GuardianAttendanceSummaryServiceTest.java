package com.dawnrise.academic.studentattendance.reporting.service;

import com.dawnrise.academic.results.integration.identity.IdentityGuardianAccessClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GuardianAttendanceSummaryServiceTest {
    @Test
    void inactiveOrForeignGuardianCannotQueryStudentAttendance() {
        @SuppressWarnings("unchecked")
        ObjectProvider<NamedParameterJdbcTemplate> provider = mock(ObjectProvider.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        IdentityGuardianAccessClient guardians = mock(IdentityGuardianAccessClient.class);
        GuardianAttendanceSummaryService service = new GuardianAttendanceSummaryService(provider, guardians);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.get(12L, 34L, 56L));

        assertEquals(404, error.getStatusCode().value());
        verify(guardians).hasActiveRelationship(12L, 34L, 56L);
        verifyNoInteractions(jdbc);
    }
}
