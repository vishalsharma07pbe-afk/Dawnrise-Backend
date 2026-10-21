package com.dawnrise.academic.timetable.service;

import com.dawnrise.academic.teacherassignment.integration.identity.BatchTeachingEligibilityResponse;
import com.dawnrise.academic.teacherassignment.integration.identity.IdentityTeacherEligibilityClient;
import com.dawnrise.academic.teacherassignment.integration.identity.TeachingEligibilityReason;
import com.dawnrise.academic.teacherassignment.integration.identity.TeachingEligibilityResponse;
import com.dawnrise.academic.timetable.exception.InvalidTimetableConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimetableTeacherSnapshotResolverTest {
    private final IdentityTeacherEligibilityClient identities=mock(IdentityTeacherEligibilityClient.class);
    private final TimetableWorkflowService service=new TimetableWorkflowService(null,null,null,null,null,identities);

    @Test
    void resolvesMultipleTeachersInOneTenantSafeBatch() {
        when(identities.checkBatch(9,List.of(11L,12L))).thenReturn(new BatchTeachingEligibilityResponse(List.of(
                teacher(11,9,"Anita Sharma"),teacher(12,9,"Rahul Singh"))));

        var names=service.resolveTeacherDisplayNames(9,List.of(11L,12L));

        assertEquals("Anita Sharma",names.get(11L));
        assertEquals("Rahul Singh",names.get(12L));
        verify(identities).checkBatch(9,List.of(11L,12L));
    }

    @Test
    void rejectsWrongTenantTeacher() {
        when(identities.checkBatch(9,List.of(11L))).thenReturn(
                new BatchTeachingEligibilityResponse(List.of(teacher(11,10,"Wrong Tenant"))));
        assertThrows(InvalidTimetableConfigurationException.class,
                ()->service.resolveTeacherDisplayNames(9,List.of(11L)));
    }

    @Test
    void rejectsMissingTeacher() {
        when(identities.checkBatch(9,List.of(11L,12L))).thenReturn(
                new BatchTeachingEligibilityResponse(List.of(teacher(11,9,"Anita Sharma"))));
        assertThrows(InvalidTimetableConfigurationException.class,
                ()->service.resolveTeacherDisplayNames(9,List.of(11L,12L)));
    }

    private TeachingEligibilityResponse teacher(long id,long organization,String name){
        return new TeachingEligibilityResponse(id,organization,name,true,TeachingEligibilityReason.ELIGIBLE);
    }
}
