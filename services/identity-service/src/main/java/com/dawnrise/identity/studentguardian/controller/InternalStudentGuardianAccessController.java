package com.dawnrise.identity.studentguardian.controller;

import com.dawnrise.identity.studentguardian.dto.GuardianStudentAccessResponse;
import com.dawnrise.identity.studentguardian.enums.StudentGuardianRelationshipStatus;
import com.dawnrise.identity.studentguardian.repository.StudentGuardianRelationshipRepository;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/internal/v1/organizations/{organizationId}/guardians/{guardianUserId}/students")
public class InternalStudentGuardianAccessController {
    private final StudentGuardianRelationshipRepository relationships;

    public InternalStudentGuardianAccessController(StudentGuardianRelationshipRepository relationships) {
        this.relationships = relationships;
    }

    @GetMapping("/{studentUserId}/active")
    @PreAuthorize("hasRole('INTERNAL_SERVICE') and authentication.name == 'academic-service'")
    public ResponseEntity<GuardianStudentAccessResponse> active(
            @PathVariable @Positive long organizationId,
            @PathVariable @Positive long guardianUserId,
            @PathVariable @Positive long studentUserId) {
        return ResponseEntity.ok(new GuardianStudentAccessResponse(
                relationships.existsByOrganizationIdAndStudentUserIdAndGuardianUserIdAndStatus(
                        organizationId, studentUserId, guardianUserId,
                        StudentGuardianRelationshipStatus.ACTIVE)));
    }
}
