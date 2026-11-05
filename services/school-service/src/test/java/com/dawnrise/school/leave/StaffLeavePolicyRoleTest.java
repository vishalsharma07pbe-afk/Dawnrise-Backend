package com.dawnrise.school.leave;

import com.dawnrise.school.leave.LeaveDtos.PolicyInput;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StaffLeavePolicyRoleTest {
    @Test
    void exposesOnlyIdentityResolvedActiveStaffRolesForTheTenant() {
        var identities = mock(IdentityStaffClient.class);
        when(identities.activeStaffRoles(8)).thenReturn(List.of("HR", "TEACHER"));
        var service = service(identities, mock(NamedParameterJdbcTemplate.class));

        assertThat(service.activeStaffRoles(8)).containsExactly("HR", "TEACHER");
        verify(identities).activeStaffRoles(8);
    }

    @Test
    void rejectsRolesThatAreNotActiveStaffRolesBeforeWritingPolicy() {
        var identities = mock(IdentityStaffClient.class);
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        when(identities.activeStaffRoles(8)).thenReturn(List.of("TEACHER", "HR"));
        var service = service(identities, jdbc);
        var input = new PolicyInput("ANNUAL", "Annual leave", LocalDate.of(2026, 1, 1), null,
                BigDecimal.TEN, false, null, null, false, true, false, false,
                Set.of("TEACHER", "STUDENT"), "ALL", null);

        assertThatThrownBy(() -> service.createPolicy(8, 12, input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active staff roles");
        verifyNoInteractions(jdbc);
    }

    private StaffLeaveService service(IdentityStaffClient identities, NamedParameterJdbcTemplate jdbc) {
        return new StaffLeaveService(jdbc, identities, mock(AcademicWorkingDayClient.class),
                mock(ObjectMapper.class), mock(LeaveUnitCalculator.class));
    }
}
