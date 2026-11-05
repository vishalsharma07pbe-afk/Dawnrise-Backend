package com.dawnrise.academic.studentleave;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentLeaveAttendanceProjectionServiceTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final StudentLeaveAttendanceProjectionService service = new StudentLeaveAttendanceProjectionService(jdbc);

    @Test
    void approvedFullDayProjectionChangesNonPresentMarkToExcused() {
        projection("EXCUSED_LEAVE");
        assertThat(service.apply(1, 20, LocalDate.of(2026, 11, 3), AttendanceStatus.ABSENT))
                .isEqualTo(AttendanceStatus.EXCUSED);
        verify(jdbc).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void approvedPartialProjectionChangesNonPresentMarkToHalfDay() {
        projection("HALF_DAY_EXCUSED");
        assertThat(service.apply(1, 20, LocalDate.of(2026, 11, 3), AttendanceStatus.ABSENT))
                .isEqualTo(AttendanceStatus.HALF_DAY);
    }

    @Test
    void approvedLeaveOverridesNewPresentMark() {
        projection("EXCUSED_LEAVE");
        assertThat(service.apply(1, 20, LocalDate.of(2026, 11, 3), AttendanceStatus.PRESENT))
                .isEqualTo(AttendanceStatus.EXCUSED);
        verify(jdbc).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rosterShowsOnlyApprovedLeaveForTheRequestedTenantSectionAndDate() throws Exception {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<Map.Entry<Long, AttendanceStatus>> mapper = invocation.getArgument(2);
                    ResultSet row = mock(ResultSet.class);
                    when(row.getLong(1)).thenReturn(20L);
                    when(row.getString(2)).thenReturn("EXCUSED_LEAVE");
                    return List.of(mapper.mapRow(row, 0));
                });

        assertThat(service.approvedForSectionDate(1, 2, 3, 4, LocalDate.of(2026, 11, 3)))
                .containsEntry(20L, AttendanceStatus.EXCUSED);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("r.status='APPROVED'", "p.attendance_date=:attendanceDate",
                "r.organization_id=:organizationId", "r.section_id=:sectionId");
        assertThat(parameters.getValue().getValue("organizationId")).isEqualTo(1L);
        assertThat(parameters.getValue().getValue("attendanceDate")).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @SuppressWarnings("unchecked")
    private void projection(String status) {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new StudentLeaveAttendanceProjectionService.Projection(5, status)));
    }
}
