package com.dawnrise.school.leave;

import com.dawnrise.school.leave.LeaveDtos.PolicyView;
import com.dawnrise.school.leave.LeaveDtos.RequestInput;
import com.dawnrise.school.leave.LeaveDtos.RequestKind;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffLeaveDraftLifecycleTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final IdentityStaffClient identities = mock(IdentityStaffClient.class);
    private final AcademicWorkingDayClient workingDays = mock(AcademicWorkingDayClient.class);
    private final StaffLeaveService service = new StaffLeaveService(jdbc, identities, workingDays,
            mock(ObjectMapper.class), new LeaveUnitCalculator());

    @Test
    void rejectsAnotherDraftForOverlappingDates() {
        LocalDate date = LocalDate.of(2026, 11, 10);
        when(identities.staff(7, 11)).thenReturn(new IdentityStaffClient.Staff(11, "ACTIVE", "Staff", Set.of("TEACHER")));
        when(jdbc.query(contains("SELECT * FROM staff_leave_types"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(policy()));
        when(jdbc.queryForList(contains("staff_leave_person_eligibility"), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn(List.of());
        when(jdbc.queryForObject(contains("retired_at IS NOT NULL"), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(false);
        when(workingDays.days(7, date, date)).thenReturn(new AcademicWorkingDayClient.Days(List.of(date), List.of()));
        when(jdbc.queryForObject(contains("status IN ('DRAFT','PENDING_CORRECTION'"), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(1);

        var input = new RequestInput(5, RequestKind.FULL_DAY, date, date, null, null, null,
                "Private reason", null, null, null);

        assertThatThrownBy(() -> service.createDraft(7, 11, input))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists for the selected dates");
    }

    @Test
    void marksOnlyTheOwnedCurrentDraftAsDeletedAndAuditsIt() {
        when(jdbc.update(contains("SET status='DELETED'"), any(MapSqlParameterSource.class))).thenReturn(1);

        service.deleteDraft(7, 11, 42, 3);

        verify(jdbc).update(contains("SET status='DELETED'"), any(MapSqlParameterSource.class));
        verify(jdbc).update(contains("staff_leave_audit"), any(MapSqlParameterSource.class));
    }

    private PolicyView policy() {
        return new PolicyView(5, "ANNUAL", "Annual leave", LocalDate.of(2026, 1, 1), null,
                BigDecimal.TEN, false, null, null, false, true, false, false,
                Set.of("TEACHER"), "ALL", 0);
    }
}
