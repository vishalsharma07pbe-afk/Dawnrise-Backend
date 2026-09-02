package com.dawnrise.academic.gradelevel.controller;

import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.config.SecurityConfig;
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
