package com.dawnrise.academic.gradelevelsubject.controller;

import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.config.SecurityConfig;
import com.dawnrise.academic.gradelevelsubject.dto.ApplyGradeLevelSubjectStructureRequest;
import com.dawnrise.academic.gradelevelsubject.dto.BulkCreateGradeLevelSubjectsRequest;
import com.dawnrise.academic.gradelevelsubject.dto.CreateGradeLevelSubjectRequest;
import com.dawnrise.academic.gradelevelsubject.dto.GradeLevelSubjectResponse;
import com.dawnrise.academic.gradelevelsubject.dto.GradeLevelSubjectStructureResponse;
import com.dawnrise.academic.gradelevelsubject.dto.UpdateGradeLevelSubjectRequest;
import com.dawnrise.academic.gradelevelsubject.service.GradeLevelSubjectService;
import com.dawnrise.academic.security.AcademicTenantSecurity;
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
        GradeLevelSubjectController.class,
        GradeLevelSubjectStructureController.class
})
@Import({
        SecurityConfig.class,
        AcademicTenantSecurity.class,
        GlobalExceptionHandler.class,
        GradeLevelSubjectControllerTest.TestConfig.class
})
class GradeLevelSubjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubGradeLevelSubjectService assignmentService;

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/subjects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrganizationIdIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/subjects")
                        .with(jwt().authorities(() -> "GRADE_SUBJECT_VIEW")))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingRequiredPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/subjects")
                        .with(jwt().jwt(jwt -> jwt.claim("organizationId", 10L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void correctPermissionAndOrganizationIdAllowAssign() throws Exception {
        assignmentService.response = response(50L);

        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/30/subjects")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_ASSIGN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.subjectId").value(40));

        assertThat(assignmentService.lastOrganizationId).isEqualTo(10L);
        assertThat(assignmentService.lastAcademicYearId).isEqualTo(20L);
        assertThat(assignmentService.lastGradeLevelId).isEqualTo(30L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowBulkAssign() throws Exception {
        assignmentService.responses = List.of(response(50L), response(51L));

        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/30/subjects/bulk")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_ASSIGN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjects": [
                                    {
                                      "subjectId": 40,
                                      "mandatory": true,
                                      "displayOrder": 1
                                    },
                                    {
                                      "subjectId": 41,
                                      "mandatory": false,
                                      "displayOrder": 2
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(50))
                .andExpect(jsonPath("$[1].id").value(51));

        assertThat(assignmentService.lastOrganizationId).isEqualTo(10L);
        assertThat(assignmentService.lastAcademicYearId).isEqualTo(20L);
        assertThat(assignmentService.lastGradeLevelId).isEqualTo(30L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowApplyStructure() throws Exception {
        assignmentService.structureResponses = List.of(
                new GradeLevelSubjectStructureResponse(
                        30L,
                        List.of(response(50L))
                ),
                new GradeLevelSubjectStructureResponse(
                        31L,
                        List.of(response(51L))
                )
        );

        mockMvc.perform(post("/api/v1/academic-years/20/grade-level-subjects/apply-structure")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_ASSIGN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gradeLevelIds": [30, 31],
                                  "subjects": [
                                    {
                                      "subjectId": 40,
                                      "mandatory": true,
                                      "displayOrder": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].gradeLevelId").value(30))
                .andExpect(jsonPath("$[1].gradeLevelId").value(31));

        assertThat(assignmentService.lastOrganizationId).isEqualTo(10L);
        assertThat(assignmentService.lastAcademicYearId).isEqualTo(20L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowGetAll() throws Exception {
        assignmentService.responses = List.of(response(50L));

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/subjects")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(50));

        assertThat(assignmentService.lastOrganizationId).isEqualTo(10L);
        assertThat(assignmentService.lastAcademicYearId).isEqualTo(20L);
        assertThat(assignmentService.lastGradeLevelId).isEqualTo(30L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowGetById() throws Exception {
        assignmentService.response = response(50L);

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/subjects/50")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50));

        assertThat(assignmentService.lastOrganizationId).isEqualTo(10L);
        assertThat(assignmentService.lastAcademicYearId).isEqualTo(20L);
        assertThat(assignmentService.lastGradeLevelId).isEqualTo(30L);
        assertThat(assignmentService.lastAssignmentId).isEqualTo(50L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowUpdate() throws Exception {
        assignmentService.response = response(50L);

        mockMvc.perform(put("/api/v1/academic-years/20/grade-levels/30/subjects/50")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50));

        assertThat(assignmentService.lastOrganizationId).isEqualTo(10L);
        assertThat(assignmentService.lastAcademicYearId).isEqualTo(20L);
        assertThat(assignmentService.lastGradeLevelId).isEqualTo(30L);
        assertThat(assignmentService.lastAssignmentId).isEqualTo(50L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowRemove() throws Exception {
        mockMvc.perform(delete("/api/v1/academic-years/20/grade-levels/30/subjects/50")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_REMOVE")))
                .andExpect(status().isNoContent());

        assertThat(assignmentService.lastOrganizationId).isEqualTo(10L);
        assertThat(assignmentService.lastAcademicYearId).isEqualTo(20L);
        assertThat(assignmentService.lastGradeLevelId).isEqualTo(30L);
        assertThat(assignmentService.lastAssignmentId).isEqualTo(50L);
        assertThat(assignmentService.removeCalls).isEqualTo(1);
    }

    @Test
    void routesRequireCorrectPermissions() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/30/subjects")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_VIEW"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/30/subjects/bulk")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_VIEW"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjects": [
                                    {
                                      "subjectId": 40,
                                      "mandatory": true,
                                      "displayOrder": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/academic-years/20/grade-level-subjects/apply-structure")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_VIEW"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gradeLevelIds": [30],
                                  "subjects": [
                                    {
                                      "subjectId": 40,
                                      "mandatory": true,
                                      "displayOrder": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/academic-years/20/grade-levels/30/subjects/50")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_VIEW"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateJson()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/academic-years/20/grade-levels/30/subjects/50")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_VIEW")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/subjects")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_ASSIGN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidRequestBodiesReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/30/subjects")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_ASSIGN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": 0,
                                  "mandatory": null,
                                  "displayOrder": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.subjectId")
                        .value("Subject ID must be positive"))
                .andExpect(jsonPath("$.validationErrors.mandatory")
                        .value("Mandatory value is required"))
                .andExpect(jsonPath("$.validationErrors.displayOrder")
                        .value("Display order must be greater than zero"));

        mockMvc.perform(put("/api/v1/academic-years/20/grade-levels/30/subjects/50")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mandatory": true,
                                  "displayOrder": 1,
                                  "version": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.version")
                        .value("Version is required"));
    }

    @Test
    void invalidPathIdsReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/0/grade-levels/30/subjects")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_VIEW")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/0/subjects")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_VIEW")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/subjects/0")
                        .with(jwtWithOrganizationAndPermission("GRADE_SUBJECT_VIEW")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
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
                  "subjectId": 40,
                  "mandatory": true,
                  "displayOrder": 1
                }
                """;
    }

    private static String validUpdateJson() {
        return """
                {
                  "mandatory": false,
                  "displayOrder": 2,
                  "version": 0
                }
                """;
    }

    private static GradeLevelSubjectResponse response(Long id) {
        return new GradeLevelSubjectResponse(
                id,
                20L,
                30L,
                40L,
                true,
                1,
                0L,
                OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubGradeLevelSubjectService gradeLevelSubjectService() {
            return new StubGradeLevelSubjectService();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("test-user")
                    .build();
        }
    }

    static class StubGradeLevelSubjectService
            implements GradeLevelSubjectService {

        private GradeLevelSubjectResponse response;
        private List<GradeLevelSubjectResponse> responses = List.of();
        private List<GradeLevelSubjectStructureResponse>
                structureResponses = List.of();
        private long lastOrganizationId;
        private long lastAcademicYearId;
        private long lastGradeLevelId;
        private long lastAssignmentId;
        private int removeCalls;

        @Override
        public GradeLevelSubjectResponse assign(
                long organizationId,
                long academicYearId,
                long gradeLevelId,
                CreateGradeLevelSubjectRequest request
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            return response;
        }

        @Override
        public List<GradeLevelSubjectResponse> assignBulk(
                long organizationId,
                long academicYearId,
                long gradeLevelId,
                BulkCreateGradeLevelSubjectsRequest request
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            return responses;
        }

        @Override
        public List<GradeLevelSubjectStructureResponse> applyStructure(
                long organizationId,
                long academicYearId,
                ApplyGradeLevelSubjectStructureRequest request
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            return structureResponses;
        }

        @Override
        public GradeLevelSubjectResponse getById(
                long organizationId,
                long academicYearId,
                long gradeLevelId,
                long assignmentId
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            lastAssignmentId = assignmentId;
            return response;
        }

        @Override
        public List<GradeLevelSubjectResponse> getAll(
                long organizationId,
                long academicYearId,
                long gradeLevelId
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            return responses;
        }

        @Override
        public GradeLevelSubjectResponse update(
                long organizationId,
                long academicYearId,
                long gradeLevelId,
                long assignmentId,
                UpdateGradeLevelSubjectRequest request
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            lastAssignmentId = assignmentId;
            return response;
        }

        @Override
        public void remove(
                long organizationId,
                long academicYearId,
                long gradeLevelId,
                long assignmentId
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            lastAssignmentId = assignmentId;
            removeCalls++;
        }
    }
}
