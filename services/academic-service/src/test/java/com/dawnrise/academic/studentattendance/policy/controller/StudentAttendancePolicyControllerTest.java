package com.dawnrise.academic.studentattendance.policy.controller;

import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.config.SecurityConfig;
import com.dawnrise.academic.security.AcademicTenantSecurity;
import com.dawnrise.academic.studentattendance.policy.dto.AttendanceStatusPolicyResponse;
import com.dawnrise.academic.studentattendance.policy.dto.StudentAttendancePolicyRequest;
import com.dawnrise.academic.studentattendance.policy.dto.StudentAttendancePolicyResponse;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceMode;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.policy.enums.LateCountingPeriod;
import com.dawnrise.academic.studentattendance.policy.enums.LatePenaltyOutcome;
import com.dawnrise.academic.studentattendance.policy.service.StudentAttendancePolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentAttendancePolicyController.class)
@Import({
        SecurityConfig.class,
        AcademicTenantSecurity.class,
        GlobalExceptionHandler.class,
        StudentAttendancePolicyControllerTest.TestConfig.class
})
class StudentAttendancePolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubStudentAttendancePolicyService service;

    @Test
    void controllerInitializationReturns201() throws Exception {
        service.response = response();

        mockMvc.perform(post("/api/v1/student-attendance/policy/initialize")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_POLICY_MANAGE"
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value(10))
                .andExpect(jsonPath("$.latePenaltyEnabled").value(true))
                .andExpect(jsonPath("$.lateOccurrencesThreshold").value(3))
                .andExpect(jsonPath("$.latePenaltyOutcome").value("HALF_DAY"))
                .andExpect(jsonPath("$.lateCountingPeriod").value("MONTHLY"));
    }

    @Test
    void controllerOrganizationAndActorIdsComeFromJwt() throws Exception {
        service.response = response();

        mockMvc.perform(post("/api/v1/student-attendance/policy/initialize")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_POLICY_MANAGE"
                        )))
                .andExpect(status().isCreated());

        assertThat(service.lastInitializeOrganizationId).isEqualTo(10L);
        assertThat(service.lastInitializeActorUserId).isEqualTo(20L);
    }

    @Test
    void policyEndpointsRequireExistingPolicyManagementAuthority() throws Exception {
        service.response = response();

        mockMvc.perform(post("/api/v1/student-attendance/policy/initialize")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_POLICY_VIEW"
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/student-attendance/policy")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_POLICY_VIEW"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPutJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void policyViewCanGet() throws Exception {
        service.response = response();

        mockMvc.perform(get("/api/v1/student-attendance/policy")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_POLICY_VIEW"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(10))
                .andExpect(jsonPath("$.latePenaltyEnabled").value(true))
                .andExpect(jsonPath("$.lateOccurrencesThreshold").value(3))
                .andExpect(jsonPath("$.latePenaltyOutcome").value("HALF_DAY"))
                .andExpect(jsonPath("$.lateCountingPeriod").value("MONTHLY"));

        assertThat(service.lastGetOrganizationId).isEqualTo(10L);
    }

    @Test
    void policyManageAloneCannotGet() throws Exception {
        service.response = response();

        mockMvc.perform(get("/api/v1/student-attendance/policy")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_POLICY_MANAGE"
                        )))
                .andExpect(status().isForbidden());
    }

    @Test
    void policyViewAloneCannotInitializeOrUpdate() throws Exception {
        service.response = response();

        mockMvc.perform(post("/api/v1/student-attendance/policy/initialize")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_POLICY_VIEW"
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/student-attendance/policy")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_POLICY_VIEW"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPutJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void postPolicyBasePathReturnsMethodNotAllowedApiError()
            throws Exception {
        mockMvc.perform(post("/api/v1/student-attendance/policy")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_POLICY_MANAGE"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPutJson()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(header().string("Allow", containsString("PUT")))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.message")
                        .value("Request method is not supported"))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/student-attendance/policy"));
    }

    @Test
    void putPolicyBasePathRemainsMappedNormally() throws Exception {
        service.response = response();

        mockMvc.perform(put("/api/v1/student-attendance/policy")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_POLICY_MANAGE"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPutJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(10))
                .andExpect(jsonPath("$.latePenaltyEnabled").value(true))
                .andExpect(jsonPath("$.lateOccurrencesThreshold").value(3))
                .andExpect(jsonPath("$.latePenaltyOutcome").value("HALF_DAY"))
                .andExpect(jsonPath("$.lateCountingPeriod").value("MONTHLY"));

        assertThat(service.lastUpdateOrganizationId).isEqualTo(10L);
        assertThat(service.lastUpdateActorUserId).isEqualTo(20L);
    }

    @Test
    void putRejectsInvalidLateThreshold() throws Exception {
        service.response = response();

        mockMvc.perform(put("/api/v1/student-attendance/policy")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_POLICY_MANAGE"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPutJson()
                                .replace("\"lateOccurrencesThreshold\": 3",
                                        "\"lateOccurrencesThreshold\": 0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putRejectsLateThresholdAboveMaximum() throws Exception {
        assertPutReturnsBadRequest(validPutJson()
                .replace("\"lateOccurrencesThreshold\": 3",
                        "\"lateOccurrencesThreshold\": 101"));
    }

    @Test
    void putRejectsMissingLatePenaltyEnabled() throws Exception {
        assertPutReturnsBadRequest(validPutJson()
                .replace("  \"latePenaltyEnabled\": true,\n", ""));
    }

    @Test
    void putRejectsNullLatePenaltyEnabled() throws Exception {
        assertPutReturnsBadRequest(validPutJson()
                .replace("\"latePenaltyEnabled\": true",
                        "\"latePenaltyEnabled\": null"));
    }

    @Test
    void putRejectsMissingLateOccurrencesThreshold() throws Exception {
        assertPutReturnsBadRequest(validPutJson()
                .replace("  \"lateOccurrencesThreshold\": 3,\n", ""));
    }

    @Test
    void putRejectsNullLateOccurrencesThreshold() throws Exception {
        assertPutReturnsBadRequest(validPutJson()
                .replace("\"lateOccurrencesThreshold\": 3",
                        "\"lateOccurrencesThreshold\": null"));
    }

    @Test
    void putRejectsMissingLatePenaltyOutcome() throws Exception {
        assertPutReturnsBadRequest(validPutJson()
                .replace("  \"latePenaltyOutcome\": \"HALF_DAY\",\n", ""));
    }

    @Test
    void putRejectsNullLatePenaltyOutcome() throws Exception {
        assertPutReturnsBadRequest(validPutJson()
                .replace("\"latePenaltyOutcome\": \"HALF_DAY\"",
                        "\"latePenaltyOutcome\": null"));
    }

    @Test
    void putRejectsMissingLateCountingPeriod() throws Exception {
        assertPutReturnsBadRequest(validPutJson()
                .replace("  \"lateCountingPeriod\": \"MONTHLY\",\n", ""));
    }

    @Test
    void putRejectsNullLateCountingPeriod() throws Exception {
        assertPutReturnsBadRequest(validPutJson()
                .replace("\"lateCountingPeriod\": \"MONTHLY\"",
                        "\"lateCountingPeriod\": null"));
    }

    @Test
    void putRejectsUnsupportedLatePenaltyOutcome() throws Exception {
        assertPutReturnsBadRequest(validPutJson()
                .replace("\"latePenaltyOutcome\": \"HALF_DAY\"",
                        "\"latePenaltyOutcome\": \"LATE\""));
    }

    @Test
    void putRejectsUnsupportedLateCountingPeriod() throws Exception {
        assertPutReturnsBadRequest(validPutJson()
                .replace("\"lateCountingPeriod\": \"MONTHLY\"",
                        "\"lateCountingPeriod\": \"WEEKLY\""));
    }

    @Test
    void postPolicyInitializeRemainsMappedNormally() throws Exception {
        service.response = response();

        mockMvc.perform(post("/api/v1/student-attendance/policy/initialize")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_POLICY_MANAGE"
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value(10));

        assertThat(service.lastInitializeOrganizationId).isEqualTo(10L);
        assertThat(service.lastInitializeActorUserId).isEqualTo(20L);
    }

    private static RequestPostProcessor jwtWithOrganizationUserAndPermission(
            String permission
    ) {
        return jwt()
                .jwt(jwt -> jwt
                        .claim("organizationId", 10L)
                        .subject("20"))
                .authorities(() -> permission);
    }

    private void assertPutReturnsBadRequest(String requestJson)
            throws Exception {
        service.response = response();

        mockMvc.perform(put("/api/v1/student-attendance/policy")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_POLICY_MANAGE"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    private static String validPutJson() {
        return """
                {
                  "attendanceMode": "DAILY",
                  "weekStartDay": "MONDAY",
                  "draftWarningMinutes": 10,
                  "automaticSubmissionMinutes": 20,
                  "latePenaltyEnabled": true,
                  "lateOccurrencesThreshold": 3,
                  "latePenaltyOutcome": "HALF_DAY",
                  "lateCountingPeriod": "MONTHLY",
                  "expectedVersion": 0,
                  "statusPolicies": [
                    {"attendanceStatus":"PRESENT","earnedCredit":1.00,"possibleCredit":1.00},
                    {"attendanceStatus":"ABSENT","earnedCredit":0.00,"possibleCredit":1.00},
                    {"attendanceStatus":"LATE","earnedCredit":0.50,"possibleCredit":1.00},
                    {"attendanceStatus":"HALF_DAY","earnedCredit":0.50,"possibleCredit":1.00},
                    {"attendanceStatus":"EXCUSED","earnedCredit":0.00,"possibleCredit":0.00}
                  ]
                }
                """;
    }

    private static StudentAttendancePolicyResponse response() {
        return new StudentAttendancePolicyResponse(
                10L,
                AttendanceMode.DAILY,
                DayOfWeek.MONDAY,
                10,
                20,
                true,
                3,
                LatePenaltyOutcome.HALF_DAY,
                LateCountingPeriod.MONTHLY,
                0L,
                OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                List.of(new AttendanceStatusPolicyResponse(
                        AttendanceStatus.PRESENT,
                        new BigDecimal("1.00"),
                        new BigDecimal("1.00"),
                        0L
                ))
        );
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubStudentAttendancePolicyService studentAttendancePolicyService() {
            return new StubStudentAttendancePolicyService();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("20")
                    .build();
        }
    }

    static class StubStudentAttendancePolicyService
            implements StudentAttendancePolicyService {

        private StudentAttendancePolicyResponse response;
        private long lastGetOrganizationId;
        private long lastInitializeOrganizationId;
        private long lastInitializeActorUserId;
        private long lastUpdateOrganizationId;
        private long lastUpdateActorUserId;

        @Override
        public StudentAttendancePolicyResponse get(long organizationId) {
            lastGetOrganizationId = organizationId;
            return response;
        }

        @Override
        public StudentAttendancePolicyResponse initialize(
                long organizationId,
                long actorUserId
        ) {
            lastInitializeOrganizationId = organizationId;
            lastInitializeActorUserId = actorUserId;
            return response;
        }

        @Override
        public StudentAttendancePolicyResponse update(
                long organizationId,
                long actorUserId,
                StudentAttendancePolicyRequest request
        ) {
            lastUpdateOrganizationId = organizationId;
            lastUpdateActorUserId = actorUserId;
            return response;
        }
    }
}
