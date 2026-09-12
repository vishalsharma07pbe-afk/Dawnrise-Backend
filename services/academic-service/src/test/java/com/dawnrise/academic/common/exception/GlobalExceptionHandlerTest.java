package com.dawnrise.academic.common.exception;

import com.dawnrise.academic.academicyear.exception.AcademicYearConflictException;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.exception.InvalidAcademicYearException;
import com.dawnrise.academic.studentprogression.enums.StudentProgressionOperationStatus;
import com.dawnrise.academic.studentprogression.exception.InvalidStudentProgressionException;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionConflictException;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionOperationNotFoundException;
import com.dawnrise.academic.teacherassignment.integration.identity.IdentityTeacherEligibilityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void invalidAcademicYearReturns400() throws Exception {
        mockMvc.perform(get("/throw/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid academic year"));
    }

    @Test
    void notFoundReturns404() throws Exception {
        mockMvc.perform(get("/throw/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Academic year not found"));
    }

    @Test
    void conflictReturns409() throws Exception {
        mockMvc.perform(get("/throw/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Academic year conflict"));
    }

    @Test
    void authorizationDeniedReturns403() throws Exception {
        mockMvc.perform(get("/throw/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access Denied"));
    }

    @Test
    void unexpectedErrorsReturnGeneric500WithoutExposingInternals()
            throws Exception {
        mockMvc.perform(get("/throw/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.message", not(containsString("database password"))));
    }

    @Test
    void identityTeacherEligibilityFailuresReturn503WithoutExposingInternals()
            throws Exception {
        mockMvc.perform(get("/throw/identity-teacher-eligibility"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("Teacher eligibility could not be verified"))
                .andExpect(jsonPath("$.message", not(containsString("test-key"))))
                .andExpect(jsonPath("$.message", not(containsString("RestClient"))))
                .andExpect(jsonPath("$.message", not(containsString("identity body"))));
    }

    @Test
    void invalidStudentProgressionReturns400() throws Exception {
        mockMvc.perform(get("/throw/invalid-student-progression"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Invalid student progression"));
    }

    @Test
    void studentProgressionOperationNotFoundReturns404()
            throws Exception {
        mockMvc.perform(get("/throw/student-progression-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Student progression operation not found"));
    }

    @Test
    void studentProgressionConflictReturns409WithFailureDetails()
            throws Exception {
        mockMvc.perform(get("/throw/student-progression-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Student progression contains blocking conflicts"))
                .andExpect(jsonPath("$.validationErrors.operationStatus")
                        .value("CONFLICTED"))
                .andExpect(jsonPath("$.validationErrors.failureCode")
                        .value("PROGRESSION_CONFLICTS"));
    }

    @Test
    void optimisticLockingFailureReturns409ApiError() throws Exception {
        mockMvc.perform(get("/throw/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Academic data conflicts with existing or newer data"))
                .andExpect(jsonPath("$.path").value("/throw/optimistic-lock"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/throw/invalid")
        void invalid() {
            throw new InvalidAcademicYearException("Invalid academic year");
        }

        @GetMapping("/throw/not-found")
        void notFound() {
            throw new AcademicYearNotFoundException("Academic year not found");
        }

        @GetMapping("/throw/conflict")
        void conflict() {
            throw new AcademicYearConflictException("Academic year conflict");
        }

        @GetMapping("/throw/forbidden")
        void forbidden() {
            throw new AuthorizationDeniedException("No permission");
        }

        @GetMapping("/throw/unexpected")
        void unexpected() {
            throw new IllegalStateException("database password leaked");
        }

        @GetMapping("/throw/identity-teacher-eligibility")
        void identityTeacherEligibility() {
            throw new IdentityTeacherEligibilityException(
                    "test-key RestClient identity body",
                    new IllegalStateException("identity body")
            );
        }

        @GetMapping("/throw/invalid-student-progression")
        void invalidStudentProgression() {
            throw new InvalidStudentProgressionException(
                    "Invalid student progression"
            );
        }

        @GetMapping("/throw/student-progression-not-found")
        void studentProgressionNotFound() {
            throw new StudentProgressionOperationNotFoundException(
                    "Student progression operation not found"
            );
        }

        @GetMapping("/throw/student-progression-conflict")
        void studentProgressionConflict() {
            throw new StudentProgressionConflictException(
                    "Student progression contains blocking conflicts",
                    StudentProgressionOperationStatus.CONFLICTED,
                    "PROGRESSION_CONFLICTS"
            );
        }

        @GetMapping("/throw/optimistic-lock")
        void optimisticLock() {
            throw new ObjectOptimisticLockingFailureException(
                    "StudentAttendancePolicy",
                    10L
            );
        }
    }
}
