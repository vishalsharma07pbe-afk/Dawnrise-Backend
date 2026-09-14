package com.dawnrise.academic.studentattendance.reporting.exception;

import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.common.integration.school.SchoolTimeZoneUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceReportExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    void invalidReportExceptionReturns400() {
        var response = handler.handleAttendanceBadRequest(
                new InvalidStudentAttendanceReportException(
                        "Invalid report range"
                ),
                request()
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message())
                .isEqualTo("Invalid report range");
    }

    @Test
    void reportNotFoundExceptionReturns404() {
        var response = handler.handleAttendanceNotFound(
                new StudentAttendanceReportNotFoundException(
                        "Submitted attendance was not found"
                ),
                request()
        );

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().message())
                .isEqualTo("Submitted attendance was not found");
    }

    @Test
    void unexpectedSqlDetailsAreNotExposed() {
        var response = handler.handleUnexpectedException(
                new IllegalStateException(
                        "select * from secret_table where tenant = 7"
                ),
                request()
        );

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message())
                .isEqualTo("An unexpected error occurred.");
        assertThat(response.getBody().message())
                .doesNotContain("secret_table")
                .doesNotContain("select");
    }

    @Test
    void accessDeniedExceptionReturns403WithoutDetails() {
        var response = handler.handleAuthorizationDenied(
                new AccessDeniedException(
                        "select * from teacher_assignments"
                ),
                request()
        );

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().message())
                .isEqualTo("Access Denied");
    }

    @Test
    void timezoneLookupFailureReturnsSafe503() {
        var response = handler.handleSchoolTimeZoneUnavailable(
                new SchoolTimeZoneUnavailableException(
                        "select time_zone from organizations"
                ),
                request()
        );

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().message())
                .isEqualTo("School timezone could not be verified");
        assertThat(response.getBody().message())
                .doesNotContain("organizations")
                .doesNotContain("select");
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest(
                "GET",
                "/api/v1/student-attendance/reports"
        );
    }
}
