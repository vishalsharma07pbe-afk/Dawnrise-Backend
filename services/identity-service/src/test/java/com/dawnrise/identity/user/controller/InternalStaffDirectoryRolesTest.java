package com.dawnrise.identity.user.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalStaffDirectoryRolesTest {
    @Test
    void listsActiveTenantStaffRolesWithoutStudentOrParent() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn(List.of("HR", "TEACHER"));
        var controller = new InternalStaffDirectoryController(jdbc);

        assertThat(controller.activeStaffRoles(8)).containsExactly("HR", "TEACHER");
        var sql = ArgumentCaptor.forClass(String.class);
        var parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForList(sql.capture(), parameters.capture(), eq(String.class));
        assertThat(sql.getValue()).contains("u.organization_id=:organizationId", "u.status='ACTIVE'", "'STUDENT','PARENT'");
        assertThat(parameters.getValue().getValue("organizationId")).isEqualTo(8L);
    }
}
