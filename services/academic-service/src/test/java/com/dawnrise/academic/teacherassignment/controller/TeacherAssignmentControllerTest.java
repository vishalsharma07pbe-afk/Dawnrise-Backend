package com.dawnrise.academic.teacherassignment.controller;

import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.config.SecurityConfig;
import com.dawnrise.academic.security.AcademicTenantSecurity;
import com.dawnrise.academic.teacherassignment.dto.BulkCreateTeacherAssignmentsRequest;
import com.dawnrise.academic.teacherassignment.dto.CreateTeacherAssignmentRequest;
import com.dawnrise.academic.teacherassignment.dto.TeacherAssignmentResponse;
import com.dawnrise.academic.teacherassignment.dto.UpdateTeacherAssignmentRequest;
import com.dawnrise.academic.teacherassignment.enums.TeacherAssignmentType;
import com.dawnrise.academic.teacherassignment.service.TeacherAssignmentService;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        TeacherAssignmentController.class,
        BulkTeacherAssignmentController.class
})
@Import({
        SecurityConfig.class,
        AcademicTenantSecurity.class,
        GlobalExceptionHandler.class,
        TeacherAssignmentControllerTest.TestConfig.class
})
class TeacherAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubTeacherAssignmentService assignmentService;

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/sections/40/teacher-assignments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrganizationIdIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/sections/40/teacher-assignments")
                        .with(jwt().authorities(() -> "TEACHER_ASSIGNMENT_VIEW")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createReadListUpdateAndDeleteUseNestedRouteAndJwtOrganization() throws Exception {
        assignmentService.response = response(70L);
        assignmentService.responses = List.of(response(70L));

        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/30/sections/40/teacher-assignments")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(70));
        assertLastIds(70L);

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/sections/40/teacher-assignments")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(70));

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/sections/40/teacher-assignments/70")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(70));

        mockMvc.perform(put("/api/v1/academic-years/20/grade-levels/30/sections/40/teacher-assignments/70")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(70));

        mockMvc.perform(delete("/api/v1/academic-years/20/grade-levels/30/sections/40/teacher-assignments/70")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_REMOVE")))
                .andExpect(status().isNoContent());
        assertThat(assignmentService.removeCalls).isEqualTo(1);
    }

    @Test
    void bulkCreateUsesYearRoutePermissionAndJwtOrganization() throws Exception {
        assignmentService.responses = List.of(
                response(70L),
                new TeacherAssignmentResponse(
                        71L,
                        20L,
                        31L,
                        41L,
                        51L,
                        61L,
                        TeacherAssignmentType.SUBJECT_TEACHER,
                        0L,
                        OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                        OffsetDateTime.parse("2026-04-01T00:00:00Z")
                )
        );

        mockMvc.perform(post("/api/v1/academic-years/20/teacher-assignments/bulk")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assignments": [
                                    {
                                      "gradeLevelId": 30,
                                      "sectionId": 40,
                                      "gradeLevelSubjectId": null,
                                      "teacherUserId": 60,
                                      "assignmentType": "CLASS_TEACHER"
                                    },
                                    {
                                      "gradeLevelId": 31,
                                      "sectionId": 41,
                                      "gradeLevelSubjectId": 51,
                                      "teacherUserId": 61,
                                      "assignmentType": "SUBJECT_TEACHER"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(70))
                .andExpect(jsonPath("$[1].id").value(71));

        assertThat(assignmentService.lastOrganizationId).isEqualTo(10L);
        assertThat(assignmentService.lastAcademicYearId).isEqualTo(20L);
    }

    @Test
    void routesRequireCorrectPermissions() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/30/sections/40/teacher-assignments")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_VIEW"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/academic-years/20/teacher-assignments/bulk")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_VIEW"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assignments": [
                                    {
                                      "gradeLevelId": 30,
                                      "sectionId": 40,
                                      "gradeLevelSubjectId": null,
                                      "teacherUserId": 60,
                                      "assignmentType": "CLASS_TEACHER"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/sections/40/teacher-assignments")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_CREATE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/academic-years/20/grade-levels/30/sections/40/teacher-assignments/70")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_VIEW"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateJson()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/academic-years/20/grade-levels/30/sections/40/teacher-assignments/70")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_VIEW")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidRequestBodiesReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/30/sections/40/teacher-assignments")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gradeLevelSubjectId": 0,
                                  "teacherUserId": 0,
                                  "assignmentType": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.gradeLevelSubjectId")
                        .value("Grade subject ID must be positive"))
                .andExpect(jsonPath("$.validationErrors.teacherUserId")
                        .value("Teacher user ID must be positive"))
                .andExpect(jsonPath("$.validationErrors.assignmentType")
                        .value("Assignment type is required"));

        mockMvc.perform(put("/api/v1/academic-years/20/grade-levels/30/sections/40/teacher-assignments/70")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teacherUserId": null,
                                  "version": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.teacherUserId")
                        .value("Teacher user ID is required"))
                .andExpect(jsonPath("$.validationErrors.version")
                        .value("Version is required"));
    }

    @Test
    void invalidPathIdsReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/0/grade-levels/30/sections/40/teacher-assignments")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_VIEW")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/0/sections/40/teacher-assignments")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_VIEW")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/sections/0/teacher-assignments")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_VIEW")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/sections/40/teacher-assignments/0")
                        .with(jwtWithOrganizationAndPermission("TEACHER_ASSIGNMENT_VIEW")))
                .andExpect(status().isBadRequest());
    }

    private void assertLastIds(Long assignmentId) {
        assertThat(assignmentService.lastOrganizationId).isEqualTo(10L);
        assertThat(assignmentService.lastAcademicYearId).isEqualTo(20L);
        assertThat(assignmentService.lastGradeLevelId).isEqualTo(30L);
        assertThat(assignmentService.lastSectionId).isEqualTo(40L);
        assertThat(assignmentService.lastAssignmentId).isEqualTo(assignmentId);
    }

    private static RequestPostProcessor jwtWithOrganizationAndPermission(
            String permission
    ) {
        return jwt()
                .jwt(jwt -> jwt.claim("organizationId", 10L))
                .authorities(() -> permission);
    }

    private static String validCreateJson() {
        return """
                {
                  "gradeLevelSubjectId": null,
                  "teacherUserId": 60,
                  "assignmentType": "CLASS_TEACHER"
                }
                """;
    }

    private static String validUpdateJson() {
        return """
                {
                  "teacherUserId": 61,
                  "version": 0
                }
                """;
    }

    private static TeacherAssignmentResponse response(Long id) {
        return new TeacherAssignmentResponse(
                id,
                20L,
                30L,
                40L,
                null,
                60L,
                TeacherAssignmentType.CLASS_TEACHER,
                0L,
                OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        StubTeacherAssignmentService teacherAssignmentService() {
            return new StubTeacherAssignmentService();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("test-user")
                    .build();
        }
    }

    static class StubTeacherAssignmentService implements TeacherAssignmentService {
        private TeacherAssignmentResponse response;
        private List<TeacherAssignmentResponse> responses = List.of();
        private long lastOrganizationId;
        private long lastAcademicYearId;
        private long lastGradeLevelId;
        private long lastSectionId;
        private long lastAssignmentId;
        private int removeCalls;

        @Override
        public TeacherAssignmentResponse create(long organizationId, long academicYearId, long gradeLevelId, long sectionId, CreateTeacherAssignmentRequest request) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            lastSectionId = sectionId;
            lastAssignmentId = 70L;
            return response;
        }

        @Override
        public List<TeacherAssignmentResponse> createBulk(
                long organizationId,
                long academicYearId,
                BulkCreateTeacherAssignmentsRequest request
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            return responses;
        }

        @Override
        public TeacherAssignmentResponse getById(long organizationId, long academicYearId, long gradeLevelId, long sectionId, long assignmentId) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            lastSectionId = sectionId;
            lastAssignmentId = assignmentId;
            return response;
        }

        @Override
        public List<TeacherAssignmentResponse> getAll(long organizationId, long academicYearId, long gradeLevelId, long sectionId) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            lastSectionId = sectionId;
            return responses;
        }

        @Override
        public TeacherAssignmentResponse update(long organizationId, long academicYearId, long gradeLevelId, long sectionId, long assignmentId, UpdateTeacherAssignmentRequest request) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            lastSectionId = sectionId;
            lastAssignmentId = assignmentId;
            return response;
        }

        @Override
        public void remove(long organizationId, long academicYearId, long gradeLevelId, long sectionId, long assignmentId) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            lastSectionId = sectionId;
            lastAssignmentId = assignmentId;
            removeCalls++;
        }
    }
}
