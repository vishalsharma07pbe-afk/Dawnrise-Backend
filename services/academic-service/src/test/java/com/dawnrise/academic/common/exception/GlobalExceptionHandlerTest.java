package com.dawnrise.academic.common.exception;

import com.dawnrise.academic.academicyear.exception.AcademicYearConflictException;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.exception.InvalidAcademicYearException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    }
}
