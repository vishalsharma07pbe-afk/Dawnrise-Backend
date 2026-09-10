package com.dawnrise.identity.studentguardian.controller;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.studentguardian.dto.CreateStudentGuardianRelationshipRequest;
import com.dawnrise.identity.studentguardian.dto.EndStudentGuardianRelationshipRequest;
import com.dawnrise.identity.studentguardian.dto.StudentGuardianRelationshipResponse;
import com.dawnrise.identity.studentguardian.service.StudentGuardianRelationshipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Validated
public class StudentGuardianRelationshipController {

    private final StudentGuardianRelationshipService service;

    public StudentGuardianRelationshipController(
            StudentGuardianRelationshipService service
    ) {
        this.service = service;
    }

    @PostMapping("/students/{studentUserId}/guardians")
    @PreAuthorize("""
        @organizationTokenSecurity.isOrganizationUser(authentication)
        and hasAuthority('STUDENT_GUARDIAN_RELATIONSHIP_MANAGE')
        """)
    public ResponseEntity<StudentGuardianRelationshipResponse> create(
            @PathVariable @Positive Long studentUserId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody
            CreateStudentGuardianRelationshipRequest request
    ) {
        StudentGuardianRelationshipResponse response = service.create(
                organizationId(jwt),
                studentUserId,
                AuthorizationContext.fromJwt(jwt),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/students/{studentUserId}/guardians")
    @PreAuthorize("""
        @organizationTokenSecurity.isOrganizationUser(authentication)
        and hasAuthority('STUDENT_GUARDIAN_RELATIONSHIP_VIEW')
        """)
    public ResponseEntity<List<StudentGuardianRelationshipResponse>>
    getActiveForStudent(
            @PathVariable @Positive Long studentUserId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(
                service.getActiveForStudent(
                        organizationId(jwt),
                        studentUserId
                )
        );
    }

    @PatchMapping(
            "/students/{studentUserId}/guardians/{relationshipId}/primary"
    )
    @PreAuthorize("""
        @organizationTokenSecurity.isOrganizationUser(authentication)
        and hasAuthority('STUDENT_GUARDIAN_RELATIONSHIP_MANAGE')
        """)
    public ResponseEntity<StudentGuardianRelationshipResponse> setPrimary(
            @PathVariable @Positive Long studentUserId,
            @PathVariable @Positive Long relationshipId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(
                service.setPrimary(
                        organizationId(jwt),
                        studentUserId,
                        relationshipId,
                        AuthorizationContext.fromJwt(jwt)
                )
        );
    }

    @PostMapping("/students/{studentUserId}/guardians/{relationshipId}/end")
    @PreAuthorize("""
        @organizationTokenSecurity.isOrganizationUser(authentication)
        and hasAuthority('STUDENT_GUARDIAN_RELATIONSHIP_MANAGE')
        """)
    public ResponseEntity<StudentGuardianRelationshipResponse> end(
            @PathVariable @Positive Long studentUserId,
            @PathVariable @Positive Long relationshipId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody
            EndStudentGuardianRelationshipRequest request
    ) {
        return ResponseEntity.ok(
                service.end(
                        organizationId(jwt),
                        studentUserId,
                        relationshipId,
                        AuthorizationContext.fromJwt(jwt),
                        request
                )
        );
    }

    @GetMapping("/students/{studentUserId}/guardian-history")
    @PreAuthorize("""
        @organizationTokenSecurity.isOrganizationUser(authentication)
        and hasAuthority('STUDENT_GUARDIAN_RELATIONSHIP_VIEW')
        """)
    public ResponseEntity<List<StudentGuardianRelationshipResponse>>
    getHistoryForStudent(
            @PathVariable @Positive Long studentUserId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(
                service.getHistoryForStudent(
                        organizationId(jwt),
                        studentUserId
                )
        );
    }

    @GetMapping("/parents/me/students")
    @PreAuthorize("""
        @organizationTokenSecurity.isOrganizationUser(authentication)
        and hasRole('PARENT')
        """)
    public ResponseEntity<List<StudentGuardianRelationshipResponse>>
    getLinkedStudentsForParent(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(
                service.getLinkedStudentsForParent(
                        organizationId(jwt),
                        AuthorizationContext.fromJwt(jwt)
                )
        );
    }

    private Long organizationId(Jwt jwt) {
        Long organizationId = jwt.getClaim("organizationId");
        if (organizationId == null) {
            throw new IllegalArgumentException(
                    "Organization ID is missing from token"
            );
        }

        return organizationId;
    }
}
