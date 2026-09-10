package com.dawnrise.identity.studentguardian.controller;

import com.dawnrise.identity.studentguardian.dto.CreateStudentGuardianRelationshipRequest;
import com.dawnrise.identity.studentguardian.dto.EndStudentGuardianRelationshipRequest;
import com.dawnrise.identity.studentguardian.service.StudentGuardianRelationshipService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.executable.ExecutableValidator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentGuardianRelationshipControllerTest {

    private final StudentGuardianRelationshipService service =
            mock(StudentGuardianRelationshipService.class);
    private final StudentGuardianRelationshipController controller =
            new StudentGuardianRelationshipController(service);
    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();
    private final ExecutableValidator executableValidator =
            validator.forExecutables();

    @Test
    void create_derivesOrganizationIdFromJwt() {
        CreateStudentGuardianRelationshipRequest request =
                new CreateStudentGuardianRelationshipRequest();

        assertEquals(
                HttpStatus.CREATED,
                controller.create(10L, jwt(), request).getStatusCode()
        );

        verify(service).create(eq(1L), eq(10L), any(), eq(request));
    }

    @Test
    void getActiveForStudent_derivesOrganizationIdFromJwt() {
        when(service.getActiveForStudent(1L, 10L))
                .thenReturn(List.of());

        assertEquals(
                HttpStatus.OK,
                controller.getActiveForStudent(10L, jwt()).getStatusCode()
        );

        verify(service).getActiveForStudent(1L, 10L);
    }

    @Test
    void setPrimary_derivesOrganizationIdFromJwt() {
        assertEquals(
                HttpStatus.OK,
                controller.setPrimary(10L, 100L, jwt()).getStatusCode()
        );

        verify(service).setPrimary(eq(1L), eq(10L), eq(100L), any());
    }

    @Test
    void end_derivesOrganizationIdFromJwt() {
        EndStudentGuardianRelationshipRequest request =
                new EndStudentGuardianRelationshipRequest();

        assertEquals(
                HttpStatus.OK,
                controller.end(10L, 100L, jwt(), request).getStatusCode()
        );

        verify(service).end(eq(1L), eq(10L), eq(100L), any(), eq(request));
    }

    @Test
    void parentSelfAccess_usesJwtSubjectThroughAuthorizationContext() {
        when(service.getLinkedStudentsForParent(eq(1L), any()))
                .thenReturn(List.of());

        assertEquals(
                HttpStatus.OK,
                controller.getLinkedStudentsForParent(jwt()).getStatusCode()
        );

        verify(service).getLinkedStudentsForParent(eq(1L), any());
    }

    @Test
    void createRequest_whenGuardianUserIdZero_failsValidation() {
        CreateStudentGuardianRelationshipRequest request =
                createRequest(0L);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void createRequest_whenGuardianUserIdNegative_failsValidation() {
        CreateStudentGuardianRelationshipRequest request =
                createRequest(-1L);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void endRequest_whenReplacementPrimaryRelationshipIdZero_failsValidation() {
        EndStudentGuardianRelationshipRequest request =
                new EndStudentGuardianRelationshipRequest();
        request.setReplacementPrimaryRelationshipId(0L);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void endRequest_whenReplacementPrimaryRelationshipIdNegative_failsValidation() {
        EndStudentGuardianRelationshipRequest request =
                new EndStudentGuardianRelationshipRequest();
        request.setReplacementPrimaryRelationshipId(-1L);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void endRequest_whenReplacementPrimaryRelationshipIdNull_passesValidation() {
        EndStudentGuardianRelationshipRequest request =
                new EndStudentGuardianRelationshipRequest();

        assertEquals(0, validator.validate(request).size());
    }

    @Test
    void controller_hasValidatedForPathVariableValidation() {
        assertEquals(
                Validated.class,
                StudentGuardianRelationshipController.class
                        .getAnnotation(Validated.class)
                        .annotationType()
        );
    }

    @Test
    void create_whenStudentPathIdZero_failsValidation()
            throws Exception {
        Set<ConstraintViolation<StudentGuardianRelationshipController>>
                violations = validateMethod(
                "create",
                new Class<?>[] {
                        Long.class,
                        Jwt.class,
                        CreateStudentGuardianRelationshipRequest.class
                },
                0L,
                jwt(),
                createRequest(20L)
        );

        assertFalse(violations.isEmpty());
    }

    @Test
    void setPrimary_whenRelationshipPathIdNegative_failsValidation()
            throws Exception {
        Set<ConstraintViolation<StudentGuardianRelationshipController>>
                violations = validateMethod(
                "setPrimary",
                new Class<?>[] {
                        Long.class,
                        Long.class,
                        Jwt.class
                },
                10L,
                -1L,
                jwt()
        );

        assertFalse(violations.isEmpty());
    }

    @Test
    void end_whenStudentPathIdNegative_failsValidation()
            throws Exception {
        Set<ConstraintViolation<StudentGuardianRelationshipController>>
                violations = validateMethod(
                "end",
                new Class<?>[] {
                        Long.class,
                        Long.class,
                        Jwt.class,
                        EndStudentGuardianRelationshipRequest.class
                },
                -1L,
                100L,
                jwt(),
                new EndStudentGuardianRelationshipRequest()
        );

        assertFalse(violations.isEmpty());
    }

    @Test
    void end_whenRelationshipPathIdZero_failsValidation()
            throws Exception {
        Set<ConstraintViolation<StudentGuardianRelationshipController>>
                violations = validateMethod(
                "end",
                new Class<?>[] {
                        Long.class,
                        Long.class,
                        Jwt.class,
                        EndStudentGuardianRelationshipRequest.class
                },
                10L,
                0L,
                jwt(),
                new EndStudentGuardianRelationshipRequest()
        );

        assertFalse(violations.isEmpty());
    }

    private Set<ConstraintViolation<StudentGuardianRelationshipController>>
    validateMethod(
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments
    ) throws Exception {
        Method method = StudentGuardianRelationshipController.class
                .getMethod(methodName, parameterTypes);

        return executableValidator.validateParameters(
                controller,
                method,
                arguments
        );
    }

    private CreateStudentGuardianRelationshipRequest createRequest(
            Long guardianUserId
    ) {
        CreateStudentGuardianRelationshipRequest request =
                new CreateStudentGuardianRelationshipRequest();
        request.setGuardianUserId(guardianUserId);
        request.setRelationshipType(
                com.dawnrise.identity.studentguardian.enums
                        .StudentGuardianRelationshipType.MOTHER
        );
        return request;
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("99")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("organizationId", 1L)
                .claim("identityType", "ORGANIZATION_USER")
                .claim("roles", List.copyOf(Set.of("PARENT")))
                .claim("permissions", List.of())
                .build();
    }
}
