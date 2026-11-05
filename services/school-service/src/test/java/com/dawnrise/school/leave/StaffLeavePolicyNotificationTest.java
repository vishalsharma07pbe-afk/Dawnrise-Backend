package com.dawnrise.school.leave;

import com.dawnrise.school.leave.LeaveDtos.PolicyInput;
import com.dawnrise.school.leave.LeaveDtos.PolicyView;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StaffLeavePolicyNotificationTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final IdentityStaffClient identity = mock(IdentityStaffClient.class);
    private final ObjectMapper json = mock(ObjectMapper.class);
    private final StaffLeaveService service = new StaffLeaveService(jdbc, identity,
            mock(AcademicWorkingDayClient.class), json, mock(LeaveUnitCalculator.class));

    @Test
    void creationQueuesStaffAudienceAfterPolicyAndAudit() throws Exception {
        when(identity.activeStaffRoles(8)).thenReturn(List.of("TEACHER"));
        when(jdbc.queryForObject(contains("INSERT INTO staff_leave_types"), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(17L);
        when(jdbc.query(contains("SELECT * FROM staff_leave_types WHERE organization_id=:org AND id=:id"),
                any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of(policy()));
        when(json.writeValueAsString(any())).thenReturn("{}");

        service.createPolicy(8, 4, input());

        verify(jdbc).update(contains("staff_leave_policy_audit"), any(MapSqlParameterSource.class));
        var event = org.mockito.ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(contains("school_notification_outbox"), event.capture());
        assertThat(event.getValue().getValue("type")).isEqualTo("LEAVE_POLICY_CREATED");
        assertThat(event.getValue().getValue("event")).isEqualTo("leave-policy:17:LEAVE_POLICY_CREATED");
    }

    @Test
    void staleRetirementDoesNotQueueNotification() {
        when(jdbc.update(contains("SET retired_at=CURRENT_TIMESTAMP"), any(MapSqlParameterSource.class))).thenReturn(0);
        assertThatThrownBy(() -> service.retirePolicy(8, 4, 17, 2))
                .isInstanceOf(OptimisticLockingFailureException.class);
        verify(jdbc, never()).update(contains("school_notification_outbox"), any(MapSqlParameterSource.class));
    }

    private PolicyInput input() {
        return new PolicyInput("SICK", "Sick leave", LocalDate.of(2026, 1, 1), null,
                BigDecimal.TEN, false, null, null, false, true, false, false, Set.of("TEACHER"), "ALL", null);
    }

    private PolicyView policy() {
        return new PolicyView(17, "SICK", "Sick leave", LocalDate.of(2026, 1, 1), null,
                BigDecimal.TEN, false, null, null, false, true, false, false, Set.of("TEACHER"), "ALL", 0);
    }
}
