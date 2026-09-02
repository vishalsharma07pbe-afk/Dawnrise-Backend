package com.dawnrise.academic.gradelevel.controller;

import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.config.SecurityConfig;
import com.dawnrise.academic.gradelevel.dto.BulkCreateGradeLevelsRequest;
import com.dawnrise.academic.gradelevel.dto.CreateGradeLevelRequest;
import com.dawnrise.academic.gradelevel.dto.GradeLevelResponse;
import com.dawnrise.academic.gradelevel.dto.UpdateGradeLevelRequest;
import com.dawnrise.academic.gradelevel.service.GradeLevelService;
import com.dawnrise.academic.security.AcademicTenantSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GradeLevelController.class)
@Import({
        SecurityConfig.class,
        AcademicTenantSecurity.class,
        GlobalExceptionHandler.class,
        GradeLevelControllerTest.TestConfig.class
})
class GradeLevelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubGradeLevelService gradeLevelService;

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/1/grade-levels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingRequiredPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/1/grade-levels")
                        .with(jwt().jwt(jwt -> jwt.claim("organizationId", 10L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void correctPermissionAndOrganizationIdAllowCreate() throws Exception {
        gradeLevelService.response = response(1L);

        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels")
                        .with(jwtWithOrganizationAndPermission("GRADE_LEVEL_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "G1",
                                  "name": "Grade 1",
                                  "displayOrder": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.code").value("G1"));

        assertThat(gradeLevelService.lastOrganizationId).isEqualTo(10L);
        assertThat(gradeLevelService.lastAcademicYearId).isEqualTo(20L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowBulkCreate() throws Exception {
        gradeLevelService.responses = List.of(response(1L), response(2L));

        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/bulk")
                        .with(jwtWithOrganizationAndPermission("GRADE_LEVEL_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gradeLevels": [
                                    {
                                      "code": "G1",
                                      "name": "Grade 1",
                                      "displayOrder": 1
                                    },
                                    {
                                      "code": "G2",
                                      "name": "Grade 2",
                                      "displayOrder": 2
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));

        assertThat(gradeLevelService.lastOrganizationId).isEqualTo(10L);
        assertThat(gradeLevelService.lastAcademicYearId).isEqualTo(20L);
    }

    @Test
    void bulkCreateRequiresGradeLevelCreatePermission() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/bulk")
                        .with(jwtWithOrganizationAndPermission("GRADE_LEVEL_VIEW"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gradeLevels": [
                                    {
                                      "code": "G1",
                                      "name": "Grade 1",
                                      "displayOrder": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void correctPermissionAndOrganizationIdAllowGetAll() throws Exception {
        gradeLevelService.responses = List.of(response(1L));

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels")
                        .with(jwtWithOrganizationAndPermission("GRADE_LEVEL_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));

        assertThat(gradeLevelService.lastOrganizationId).isEqualTo(10L);
        assertThat(gradeLevelService.lastAcademicYearId).isEqualTo(20L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowGetById() throws Exception {
        gradeLevelService.response = response(1L);

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/1")
                        .with(jwtWithOrganizationAndPermission("GRADE_LEVEL_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        assertThat(gradeLevelService.lastAcademicYearId).isEqualTo(20L);
        assertThat(gradeLevelService.lastGradeLevelId).isEqualTo(1L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowUpdate() throws Exception {
        gradeLevelService.response = response(1L);

        mockMvc.perform(put("/api/v1/academic-years/20/grade-levels/1")
                        .with(jwtWithOrganizationAndPermission("GRADE_LEVEL_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "G1",
                                  "name": "Grade 1",
                                  "displayOrder": 1,
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        assertThat(gradeLevelService.lastAcademicYearId).isEqualTo(20L);
        assertThat(gradeLevelService.lastGradeLevelId).isEqualTo(1L);
    }

    @Test
    void invalidRequestBodiesReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels")
                        .with(jwtWithOrganizationAndPermission("GRADE_LEVEL_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "G 1",
                                  "name": "",
                                  "displayOrder": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.code")
                        .value("Grade level code can contain only letters, numbers, hyphens, and underscores"))
                .andExpect(jsonPath("$.validationErrors.name")
                        .value("Grade level name is required"))
                .andExpect(jsonPath("$.validationErrors.displayOrder")
                        .value("Display order must be greater than zero"));
    }

    @Test
    void invalidBulkCreateRequestBodiesReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/bulk")
                        .with(jwtWithOrganizationAndPermission("GRADE_LEVEL_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gradeLevels": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.gradeLevels")
                        .value("At least one grade level is required"));

        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/bulk")
                        .with(jwtWithOrganizationAndPermission("GRADE_LEVEL_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gradeLevels": [
                                    {"code":"G01","name":"Grade 01","displayOrder":1},
                                    {"code":"G02","name":"Grade 02","displayOrder":2},
                                    {"code":"G03","name":"Grade 03","displayOrder":3},
                                    {"code":"G04","name":"Grade 04","displayOrder":4},
                                    {"code":"G05","name":"Grade 05","displayOrder":5},
                                    {"code":"G06","name":"Grade 06","displayOrder":6},
                                    {"code":"G07","name":"Grade 07","displayOrder":7},
                                    {"code":"G08","name":"Grade 08","displayOrder":8},
                                    {"code":"G09","name":"Grade 09","displayOrder":9},
                                    {"code":"G10","name":"Grade 10","displayOrder":10},
                                    {"code":"G11","name":"Grade 11","displayOrder":11},
                                    {"code":"G12","name":"Grade 12","displayOrder":12},
                                    {"code":"G13","name":"Grade 13","displayOrder":13},
                                    {"code":"G14","name":"Grade 14","displayOrder":14},
                                    {"code":"G15","name":"Grade 15","displayOrder":15},
                                    {"code":"G16","name":"Grade 16","displayOrder":16},
                                    {"code":"G17","name":"Grade 17","displayOrder":17},
                                    {"code":"G18","name":"Grade 18","displayOrder":18},
                                    {"code":"G19","name":"Grade 19","displayOrder":19},
                                    {"code":"G20","name":"Grade 20","displayOrder":20},
                                    {"code":"G21","name":"Grade 21","displayOrder":21},
                                    {"code":"G22","name":"Grade 22","displayOrder":22},
                                    {"code":"G23","name":"Grade 23","displayOrder":23},
                                    {"code":"G24","name":"Grade 24","displayOrder":24},
                                    {"code":"G25","name":"Grade 25","displayOrder":25},
                                    {"code":"G26","name":"Grade 26","displayOrder":26},
                                    {"code":"G27","name":"Grade 27","displayOrder":27},
                                    {"code":"G28","name":"Grade 28","displayOrder":28},
                                    {"code":"G29","name":"Grade 29","displayOrder":29},
                                    {"code":"G30","name":"Grade 30","displayOrder":30},
                                    {"code":"G31","name":"Grade 31","displayOrder":31},
                                    {"code":"G32","name":"Grade 32","displayOrder":32},
                                    {"code":"G33","name":"Grade 33","displayOrder":33},
                                    {"code":"G34","name":"Grade 34","displayOrder":34},
                                    {"code":"G35","name":"Grade 35","displayOrder":35},
                                    {"code":"G36","name":"Grade 36","displayOrder":36},
                                    {"code":"G37","name":"Grade 37","displayOrder":37},
                                    {"code":"G38","name":"Grade 38","displayOrder":38},
                                    {"code":"G39","name":"Grade 39","displayOrder":39},
                                    {"code":"G40","name":"Grade 40","displayOrder":40},
                                    {"code":"G41","name":"Grade 41","displayOrder":41},
                                    {"code":"G42","name":"Grade 42","displayOrder":42},
                                    {"code":"G43","name":"Grade 43","displayOrder":43},
                                    {"code":"G44","name":"Grade 44","displayOrder":44},
                                    {"code":"G45","name":"Grade 45","displayOrder":45},
                                    {"code":"G46","name":"Grade 46","displayOrder":46},
                                    {"code":"G47","name":"Grade 47","displayOrder":47},
                                    {"code":"G48","name":"Grade 48","displayOrder":48},
                                    {"code":"G49","name":"Grade 49","displayOrder":49},
                                    {"code":"G50","name":"Grade 50","displayOrder":50},
                                    {"code":"G51","name":"Grade 51","displayOrder":51}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.gradeLevels")
                        .value("A maximum of 50 grade levels can be created at once"));
    }

    @Test
    void invalidIdsReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/0/grade-levels")
                        .with(jwtWithOrganizationAndPermission("GRADE_LEVEL_VIEW")))
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

    private static GradeLevelResponse response(Long id) {
        return new GradeLevelResponse(
                id,
                20L,
                "G1",
                "Grade 1",
                1,
                0L,
                OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubGradeLevelService gradeLevelService() {
            return new StubGradeLevelService();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("test-user")
                    .build();
        }
    }

    static class StubGradeLevelService implements GradeLevelService {

        private GradeLevelResponse response;
        private List<GradeLevelResponse> responses = List.of();
        private long lastOrganizationId;
        private long lastAcademicYearId;
        private long lastGradeLevelId;

        @Override
        public GradeLevelResponse create(
                long organizationId,
                long academicYearId,
                CreateGradeLevelRequest request
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            return response;
        }

        @Override
        public GradeLevelResponse getById(
                long organizationId,
                long academicYearId,
                long gradeLevelId
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            return response;
        }

        @Override
        public List<GradeLevelResponse> getAll(
                long organizationId,
                long academicYearId
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            return responses;
        }

        @Override
        public List<GradeLevelResponse> createBulk(
                long organizationId,
                long academicYearId,
                BulkCreateGradeLevelsRequest request
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            return responses;
        }

        @Override
        public GradeLevelResponse update(
                long organizationId,
                long academicYearId,
                long gradeLevelId,
                UpdateGradeLevelRequest request
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            return response;
        }
    }
}
