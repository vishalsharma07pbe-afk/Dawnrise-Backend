package com.dawnrise.school.communication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunicationAudienceServiceTest {
    private CommunicationDirectoryGateway gateway;
    private CommunicationAudienceService service;
    @BeforeEach void setUp(){ gateway=mock(CommunicationDirectoryGateway.class); service=new CommunicationAudienceService(gateway); }

    @Test void scopedTeacherSearchIsLimitedToAssignedStudentsAndTheirActiveGuardians(){
        when(gateway.scopeStudents(7,42,false)).thenReturn(Set.of(100L));
        when(gateway.guardians(7,Set.of(100L))).thenReturn(Set.of(200L));
        when(gateway.identityAudience(7,"STAFF",null)).thenReturn(Set.of(42L,50L));
        when(gateway.searchUsers(anyLong(),anyString(),anyInt(),anyInt(),org.mockito.ArgumentMatchers.anySet(),org.mockito.ArgumentMatchers.anySet()))
                .thenReturn(new CommunicationDirectoryGateway.IdentityPage(List.of(),0,20,0));
        service.recipients(7,42,false,"",0,20);
        @SuppressWarnings("unchecked") ArgumentCaptor<Set<Long>> allowed=ArgumentCaptor.forClass(Set.class);
        verify(gateway).searchUsers(eq(7L),eq(""),eq(0),eq(20),allowed.capture(),eq(Set.of(42L)));
        assertEquals(Set.of(42L,50L,100L,200L),allowed.getValue());
    }

    @Test void scopedSearchSupportsStaffOnlyWhenTeacherHasNoAssignedStudents(){
        when(gateway.scopeStudents(7,42,false)).thenReturn(Set.of());
        when(gateway.identityAudience(7,"STAFF",null)).thenReturn(Set.of(42L,50L));
        when(gateway.searchUsers(anyLong(),anyString(),anyInt(),anyInt(),org.mockito.ArgumentMatchers.anySet(),org.mockito.ArgumentMatchers.anySet()))
                .thenReturn(new CommunicationDirectoryGateway.IdentityPage(List.of(),0,20,0));

        service.recipients(7,42,false,"",0,20);

        @SuppressWarnings("unchecked") ArgumentCaptor<Set<Long>> allowed=ArgumentCaptor.forClass(Set.class);
        verify(gateway).searchUsers(eq(7L),eq(""),eq(0),eq(20),allowed.capture(),eq(Set.of(42L)));
        assertEquals(Set.of(42L,50L),allowed.getValue());
    }

    @Test void scopedTeacherCanSelectActiveStaffButNotUnassignedStudents(){
        when(gateway.scopeStudents(7,42,false)).thenReturn(Set.of(100L));
        when(gateway.guardians(7,Set.of(100L))).thenReturn(Set.of(200L));
        when(gateway.identityAudience(7,"STAFF",null)).thenReturn(Set.of(42L,50L));
        when(gateway.resolveUsers(7,Set.of(50L))).thenReturn(Set.of(50L));
        var staff=new CommunicationDtos.PreviewRequest(List.of(new CommunicationDtos.Selection(CommunicationDtos.Type.INDIVIDUAL,"50")));
        assertEquals(1,service.preview(7,42,false,staff).recipientCount());
        var unassignedStudent=new CommunicationDtos.PreviewRequest(List.of(new CommunicationDtos.Selection(CommunicationDtos.Type.INDIVIDUAL,"999")));
        assertThrows(IllegalArgumentException.class,()->service.preview(7,42,false,unassignedStudent));
    }

    @Test void rejectsUnauthorizedDirectIndividualIdentifier(){
        when(gateway.scopeStudents(7,42,false)).thenReturn(Set.of(100L));
        when(gateway.guardians(7,Set.of(100L))).thenReturn(Set.of());
        var request=new CommunicationDtos.PreviewRequest(List.of(new CommunicationDtos.Selection(CommunicationDtos.Type.INDIVIDUAL,"999")));
        assertThrows(IllegalArgumentException.class,()->service.preview(7,42,false,request));
    }

    @Test void previewDeduplicatesOverlappingAudienceUnionAndSelections(){
        when(gateway.identityAudience(7,"STUDENT",null)).thenReturn(Set.of(1L,2L));
        when(gateway.identityAudience(7,"ACTIVE_GUARDIAN",null)).thenReturn(Set.of(2L,3L));
        var request=new CommunicationDtos.PreviewRequest(List.of(
                new CommunicationDtos.Selection(CommunicationDtos.Type.STUDENT,null),
                new CommunicationDtos.Selection(CommunicationDtos.Type.STUDENT,null),
                new CommunicationDtos.Selection(CommunicationDtos.Type.ACTIVE_GUARDIAN,null)));
        var preview=service.preview(7,42,true,request);
        assertEquals(3,preview.recipientCount()); assertEquals(2,preview.selections().size());
        assertEquals("AT_DELIVERY_TIME",preview.membershipResolution());
    }

    @Test void previewDeduplicatesOverlappingFamilyAudiences(){
        when(gateway.academicAudience(7,42,true,"SECTION_FAMILIES",10)).thenReturn(Set.of(100L,101L));
        when(gateway.academicAudience(7,42,true,"GRADE_FAMILIES",20)).thenReturn(Set.of(100L,101L,102L));
        when(gateway.guardians(7,Set.of(100L,101L))).thenReturn(Set.of(201L,202L));
        when(gateway.guardians(7,Set.of(100L,101L,102L))).thenReturn(Set.of(201L,202L,203L));
        var request=new CommunicationDtos.PreviewRequest(List.of(
                new CommunicationDtos.Selection(CommunicationDtos.Type.SECTION_FAMILIES,"10"),
                new CommunicationDtos.Selection(CommunicationDtos.Type.GRADE_FAMILIES,"20")));
        assertEquals(3,service.preview(7,42,true,request).recipientCount());
    }

    @Test void previewReturnsPerChannelEligibilityWithoutContactDetails(){
        when(gateway.resolveUsers(7,Set.of(50L))).thenReturn(Set.of(50L));
        when(gateway.eligibility(7,Set.of(50L),Set.of("APP","EMAIL"))).thenReturn(List.of(
                new CommunicationDirectoryGateway.ChannelEligibility("APP",1,0,true,null),
                new CommunicationDirectoryGateway.ChannelEligibility("EMAIL",0,1,true,null)));
        var request=new CommunicationDtos.PreviewRequest(List.of(
                new CommunicationDtos.Selection(CommunicationDtos.Type.INDIVIDUAL,"50")),
                List.of(CommunicationDtos.Channel.APP,CommunicationDtos.Channel.EMAIL));
        var preview=service.preview(7,42,true,request);
        assertEquals(1,preview.recipientCount());
        assertEquals(1,preview.channels().getFirst().eligibleCount());
        assertEquals(1,preview.channels().get(1).excludedCount());
    }

    @Test void groupDiscoveryIsBoundedAndPaginated(){
        when(gateway.identityGroups(7)).thenReturn(List.of(
                new CommunicationDirectoryGateway.IdentityGroup("STAFF",null,"Staff","Active"),
                new CommunicationDirectoryGateway.IdentityGroup("STUDENT",null,"Students","Active")));
        when(gateway.academicGroups(7,42,true)).thenReturn(List.of());
        var page=service.groups(7,42,true,"",1,2);
        assertEquals(2,page.size()); assertEquals(1,page.page()); assertEquals(3,page.total()); assertEquals(1,page.items().size());
    }
}
