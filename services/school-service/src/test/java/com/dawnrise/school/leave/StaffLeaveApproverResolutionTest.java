package com.dawnrise.school.leave;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StaffLeaveApproverResolutionTest {
    private final NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
    private final IdentityStaffClient identities=mock(IdentityStaffClient.class);
    private final StaffLeaveService service=new StaffLeaveService(jdbc,identities,
            mock(AcademicWorkingDayClient.class),mock(ObjectMapper.class),mock(LeaveUnitCalculator.class));

    @Test
    void usesActivePrincipalWhenNoApproverMappingExists(){
        noMappings();
        when(identities.approvers(7,List.of("PRINCIPAL"))).thenReturn(List.of(
                new IdentityStaffClient.Staff(14,"ACTIVE","Principal",Set.of("PRINCIPAL"))));

        assertEquals(List.of(new StaffLeaveService.ApprovalTarget("PRINCIPAL",14)),
                service.approvalChain(7,11,Set.of("ADMIN")));
    }

    @Test
    void usesAdministratorForPrincipalRequestWithoutMapping(){
        noMappings();
        when(identities.approvers(7,List.of("ADMIN"))).thenReturn(List.of(
                new IdentityStaffClient.Staff(11,"ACTIVE","Administrator",Set.of("ADMIN"))));

        assertEquals(List.of(new StaffLeaveService.ApprovalTarget("ADMIN",11)),
                service.approvalChain(7,14,Set.of("PRINCIPAL")));
    }

    @Test
    void configuredMappingStillTakesPriority(){
        doReturn(List.of(new StaffLeaveService.ApprovalTarget("HR",20))).when(jdbc)
                .query(anyString(),any(MapSqlParameterSource.class),any(RowMapper.class));

        assertEquals(List.of(new StaffLeaveService.ApprovalTarget("HR",20)),
                service.approvalChain(7,11,Set.of("ADMIN")));
        verifyNoInteractions(identities);
    }

    private void noMappings(){
        doReturn(List.of()).when(jdbc)
                .query(anyString(),any(MapSqlParameterSource.class),any(RowMapper.class));
    }
}
