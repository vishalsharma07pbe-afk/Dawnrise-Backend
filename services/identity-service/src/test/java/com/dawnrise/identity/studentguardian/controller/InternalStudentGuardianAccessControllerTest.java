package com.dawnrise.identity.studentguardian.controller;

import com.dawnrise.identity.studentguardian.enums.StudentGuardianRelationshipStatus;
import com.dawnrise.identity.studentguardian.repository.StudentGuardianRelationshipRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalStudentGuardianAccessControllerTest {

    @Test
    void returnsOnlyTheActiveTenantScopedRelationshipResult() {
        StudentGuardianRelationshipRepository relationships =
                mock(StudentGuardianRelationshipRepository.class);
        InternalStudentGuardianAccessController controller =
                new InternalStudentGuardianAccessController(relationships);

        when(relationships.existsByOrganizationIdAndStudentUserIdAndGuardianUserIdAndStatus(
                1L, 21L, 90L, StudentGuardianRelationshipStatus.ACTIVE))
                .thenReturn(true);

        assertTrue(controller.active(1L, 90L, 21L).getBody().active());
        assertFalse(controller.active(1L, 90L, 22L).getBody().active());
        verify(relationships).existsByOrganizationIdAndStudentUserIdAndGuardianUserIdAndStatus(
                1L, 21L, 90L, StudentGuardianRelationshipStatus.ACTIVE);
        verify(relationships).existsByOrganizationIdAndStudentUserIdAndGuardianUserIdAndStatus(
                1L, 22L, 90L, StudentGuardianRelationshipStatus.ACTIVE);
    }
}
