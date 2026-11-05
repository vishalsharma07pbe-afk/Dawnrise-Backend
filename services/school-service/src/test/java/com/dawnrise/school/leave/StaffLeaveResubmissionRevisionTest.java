package com.dawnrise.school.leave;

import com.dawnrise.school.leave.LeaveDtos.RequestKind;
import com.dawnrise.school.leave.LeaveDtos.RequestView;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffLeaveResubmissionRevisionTest {
    private final NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
    private final StaffLeaveService service=new StaffLeaveService(jdbc,mock(IdentityStaffClient.class),mock(AcademicWorkingDayClient.class),mock(ObjectMapper.class),new LeaveUnitCalculator());

    @Test
    void advancesRevisionWhenReturnedRequestWasNotEditedBeforeResubmission(){
        when(jdbc.queryForObject(contains("SELECT current_revision"),any(MapSqlParameterSource.class),eq(Integer.class))).thenReturn(1);
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM staff_leave_approvals"),any(MapSqlParameterSource.class),eq(Integer.class))).thenReturn(1);

        int revision=service.approvalRevision(7,5,12,request(),true);

        assertThat(revision).isEqualTo(2);
        verify(jdbc).update(contains("SET superseded=TRUE"),any(MapSqlParameterSource.class));
        verify(jdbc).update(contains("SET current_revision=current_revision+1"),any(MapSqlParameterSource.class));
        verify(jdbc).update(contains("INSERT INTO staff_leave_revisions"),any(MapSqlParameterSource.class));
    }

    @Test
    void reusesRevisionCreatedByAnEditedCorrection(){
        when(jdbc.queryForObject(contains("SELECT current_revision"),any(MapSqlParameterSource.class),eq(Integer.class))).thenReturn(2);
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM staff_leave_approvals"),any(MapSqlParameterSource.class),eq(Integer.class))).thenReturn(0);

        assertThat(service.approvalRevision(7,5,12,request(),true)).isEqualTo(2);
    }

    @Test
    void approvalQueueSelectsOnlyTheCurrentPendingRevision(){
        when(jdbc.queryForList(contains("SELECT r.id FROM staff_leave_requests"),any(MapSqlParameterSource.class),eq(Long.class))).thenReturn(List.of());

        service.queue(7,14);

        verify(jdbc).queryForList(contains("a.revision_number=r.current_revision"),any(MapSqlParameterSource.class),eq(Long.class));
    }

    private RequestView request(){return new RequestView(5,12,3,"Annual leave",RequestKind.FULL_DAY,LocalDate.of(2026,11,10),LocalDate.of(2026,11,10),null,null,null,BigDecimal.ONE,"Medical evidence added","evidence.png","PENDING_CORRECTION",0,2,OffsetDateTime.now(),List.of());}
}
