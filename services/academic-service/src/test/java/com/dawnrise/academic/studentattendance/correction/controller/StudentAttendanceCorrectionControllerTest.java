package com.dawnrise.academic.studentattendance.correction.controller;

import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.config.SecurityConfig;
import com.dawnrise.academic.security.AcademicTenantSecurity;
import com.dawnrise.academic.studentattendance.correction.dto.CreateStudentAttendanceCorrectionRequest;
import com.dawnrise.academic.studentattendance.correction.dto.StudentAttendanceCorrectionDecisionRequest;
import com.dawnrise.academic.studentattendance.correction.dto.StudentAttendanceCorrectionResponse;
import com.dawnrise.academic.studentattendance.correction.enums.StudentAttendanceCorrectionStatus;
import com.dawnrise.academic.studentattendance.correction.service.StudentAttendanceCorrectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentAttendanceCorrectionController.class)
@Import({
        SecurityConfig.class,
        AcademicTenantSecurity.class,
        GlobalExceptionHandler.class,
        StudentAttendanceCorrectionControllerTest.TestConfig.class
})
class StudentAttendanceCorrectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubStudentAttendanceCorrectionService service;

    @Test
    void createRequiresCorrectionRequestAuthorityAndUsesJwtContext() throws Exception {
        service.response = response(StudentAttendanceCorrectionStatus.PENDING);

        mockMvc.perform(post("/api/v1/student-attendance/corrections")
                        .queryParam("attendanceSessionId", "70")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CORRECTION_REQUEST"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(service.lastOrganizationId).isEqualTo(10L);
        assertThat(service.lastActorUserId).isEqualTo(20L);
        assertThat(service.lastAttendanceSessionId).isEqualTo(70L);
    }

    @Test
    void viewAuthorityCanGetAndListButCannotApprove() throws Exception {
        service.response = response(StudentAttendanceCorrectionStatus.PENDING);

        mockMvc.perform(get("/api/v1/student-attendance/corrections/90")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CORRECTION_VIEW"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(90));

        mockMvc.perform(get("/api/v1/student-attendance/corrections")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CORRECTION_VIEW"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(90));

        mockMvc.perform(post("/api/v1/student-attendance/corrections/90/approve")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CORRECTION_VIEW"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestAuthorityCanCancelButCannotReject() throws Exception {
        service.response = response(StudentAttendanceCorrectionStatus.CANCELLED);

        mockMvc.perform(post("/api/v1/student-attendance/corrections/90/cancel")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CORRECTION_REQUEST"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/student-attendance/corrections/90/reject")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CORRECTION_REQUEST"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveAuthorityCanApproveAndReject() throws Exception {
        service.response = response(StudentAttendanceCorrectionStatus.APPROVED);

        mockMvc.perform(post("/api/v1/student-attendance/corrections/90/approve")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CORRECTION_APPROVE"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        service.response = response(StudentAttendanceCorrectionStatus.REJECTED);

        mockMvc.perform(post("/api/v1/student-attendance/corrections/90/reject")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CORRECTION_APPROVE"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void approveAcceptsMissingReviewComment() throws Exception {
        service.response = response(StudentAttendanceCorrectionStatus.APPROVED);

        mockMvc.perform(post("/api/v1/student-attendance/corrections/90/approve")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CORRECTION_APPROVE"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJsonWithoutComment()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(service.lastDecision.reviewComment()).isNull();
    }

    @Test
    void cancelAcceptsMissingReviewComment() throws Exception {
        service.response = response(StudentAttendanceCorrectionStatus.CANCELLED);

        mockMvc.perform(post("/api/v1/student-attendance/corrections/90/cancel")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CORRECTION_REQUEST"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJsonWithoutComment()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(service.lastDecision.reviewComment()).isNull();
    }

    @Test
    void rejectForwardsReviewComment() throws Exception {
        service.response = response(StudentAttendanceCorrectionStatus.REJECTED);

        mockMvc.perform(post("/api/v1/student-attendance/corrections/90/reject")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CORRECTION_APPROVE"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(service.lastDecision.reviewComment()).isEqualTo("Reviewed");
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

    private static String createJson() {
        return """
                {
                  "reason": "Wrong mark",
                  "items": [
                    {
                      "attendanceRecordId": 80,
                      "expectedRecordVersion": 0,
                      "proposedRecordedStatus": "PRESENT",
                      "proposedRemarks": "Corrected"
                    }
                  ]
                }
                """;
    }

    private static String decisionJson() {
        return """
                {
                  "expectedVersion": 0,
                  "reviewComment": "Reviewed"
                }
                """;
    }

    private static String decisionJsonWithoutComment() {
        return """
                {
                  "expectedVersion": 0
                }
                """;
    }

    private static StudentAttendanceCorrectionResponse response(
            StudentAttendanceCorrectionStatus status
    ) {
        return new StudentAttendanceCorrectionResponse(
                90L,
                10L,
                20L,
                30L,
                40L,
                70L,
                "Wrong mark",
                status,
                50L,
                status == StudentAttendanceCorrectionStatus.PENDING ? null : 20L,
                status == StudentAttendanceCorrectionStatus.PENDING ? null : "Reviewed",
                OffsetDateTime.parse("2026-09-12T07:00:00Z"),
                status == StudentAttendanceCorrectionStatus.PENDING
                        ? null
                        : OffsetDateTime.parse("2026-09-12T08:00:00Z"),
                0L,
                OffsetDateTime.parse("2026-09-12T07:00:00Z"),
                OffsetDateTime.parse("2026-09-12T07:00:00Z"),
                List.of()
        );
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubStudentAttendanceCorrectionService studentAttendanceCorrectionService() {
            return new StubStudentAttendanceCorrectionService();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("20")
                    .build();
        }
    }

    static class StubStudentAttendanceCorrectionService
            implements StudentAttendanceCorrectionService {

        private StudentAttendanceCorrectionResponse response;
        private long lastOrganizationId;
        private long lastActorUserId;
        private long lastAttendanceSessionId;
        private StudentAttendanceCorrectionDecisionRequest lastDecision;

        @Override
        public StudentAttendanceCorrectionResponse create(
                long organizationId,
                long actorUserId,
                long attendanceSessionId,
                CreateStudentAttendanceCorrectionRequest request
        ) {
            lastOrganizationId = organizationId;
            lastActorUserId = actorUserId;
            lastAttendanceSessionId = attendanceSessionId;
            return response;
        }

        @Override
        public StudentAttendanceCorrectionResponse get(
                long organizationId,
                long actorUserId,
                long requestId
        ) {
            return response;
        }

        @Override
        public Page<StudentAttendanceCorrectionResponse> list(
                long organizationId,
                long actorUserId,
                Long attendanceSessionId,
                StudentAttendanceCorrectionStatus status,
                Pageable pageable
        ) {
            return new PageImpl<>(List.of(response));
        }

        @Override
        public StudentAttendanceCorrectionResponse cancel(
                long organizationId,
                long actorUserId,
                long requestId,
                StudentAttendanceCorrectionDecisionRequest decision
        ) {
            lastDecision = decision;
            return response;
        }

        @Override
        public StudentAttendanceCorrectionResponse reject(
                long organizationId,
                long actorUserId,
                long requestId,
                StudentAttendanceCorrectionDecisionRequest decision
        ) {
            lastDecision = decision;
            return response;
        }

        @Override
        public StudentAttendanceCorrectionResponse approve(
                long organizationId,
                long actorUserId,
                long requestId,
                StudentAttendanceCorrectionDecisionRequest decision
        ) {
            lastDecision = decision;
            return response;
        }
    }
}
