package com.dawnrise.academic.section.controller;

import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.config.SecurityConfig;
import com.dawnrise.academic.section.dto.ApplySectionStructureRequest;
import com.dawnrise.academic.section.dto.BulkCreateSectionsRequest;
import com.dawnrise.academic.section.dto.CreateSectionRequest;
import com.dawnrise.academic.section.dto.SectionResponse;
import com.dawnrise.academic.section.dto.UpdateSectionRequest;
import com.dawnrise.academic.section.service.SectionService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SectionStructureController.class)
@Import({
        SecurityConfig.class,
        AcademicTenantSecurity.class,
        GlobalExceptionHandler.class,
        SectionStructureControllerTest.TestConfig.class
})
class SectionStructureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubSectionService sectionService;

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/sections/apply-structure"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingRequiredPermissionIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/sections/apply-structure")
                        .with(jwtWithOrganizationAndPermission("SECTION_VIEW"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void correctPermissionAndOrganizationIdAllowApplyStructure() throws Exception {
        sectionService.responses = List.of(response(1L, 30L), response(2L, 31L));

        mockMvc.perform(post("/api/v1/academic-years/20/sections/apply-structure")
                        .with(jwtWithOrganizationAndPermission("SECTION_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].gradeLevelId").value(30))
                .andExpect(jsonPath("$[1].gradeLevelId").value(31));

        assertThat(sectionService.lastOrganizationId).isEqualTo(10L);
        assertThat(sectionService.lastAcademicYearId).isEqualTo(20L);
    }

    @Test
    void invalidAcademicYearIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/0/sections/apply-structure")
                        .with(jwtWithOrganizationAndPermission("SECTION_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }

    @Test
    void invalidBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/sections/apply-structure")
                        .with(jwtWithOrganizationAndPermission("SECTION_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gradeLevelIds": [],
                                  "sections": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.gradeLevelIds")
                        .value("At least one grade level is required"))
                .andExpect(jsonPath("$.validationErrors.sections")
                        .value("At least one section is required"));
    }

    @Test
    void overMaximumSectionDefinitionsReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/sections/apply-structure")
                        .with(jwtWithOrganizationAndPermission("SECTION_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gradeLevelIds": [30],
                                  "sections": [
                                    {"code":"A01","name":"Section 01","displayOrder":1},
                                    {"code":"A02","name":"Section 02","displayOrder":2},
                                    {"code":"A03","name":"Section 03","displayOrder":3},
                                    {"code":"A04","name":"Section 04","displayOrder":4},
                                    {"code":"A05","name":"Section 05","displayOrder":5},
                                    {"code":"A06","name":"Section 06","displayOrder":6},
                                    {"code":"A07","name":"Section 07","displayOrder":7},
                                    {"code":"A08","name":"Section 08","displayOrder":8},
                                    {"code":"A09","name":"Section 09","displayOrder":9},
                                    {"code":"A10","name":"Section 10","displayOrder":10},
                                    {"code":"A11","name":"Section 11","displayOrder":11},
                                    {"code":"A12","name":"Section 12","displayOrder":12},
                                    {"code":"A13","name":"Section 13","displayOrder":13},
                                    {"code":"A14","name":"Section 14","displayOrder":14},
                                    {"code":"A15","name":"Section 15","displayOrder":15},
                                    {"code":"A16","name":"Section 16","displayOrder":16},
                                    {"code":"A17","name":"Section 17","displayOrder":17},
                                    {"code":"A18","name":"Section 18","displayOrder":18},
                                    {"code":"A19","name":"Section 19","displayOrder":19},
                                    {"code":"A20","name":"Section 20","displayOrder":20},
                                    {"code":"A21","name":"Section 21","displayOrder":21}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.sections")
                        .value("A maximum of 20 sections can be applied to each grade"));
    }

    private static String validBody() {
        return """
                {
                  "gradeLevelIds": [30, 31],
                  "sections": [
                    {
                      "code": "A",
                      "name": "Section A",
                      "displayOrder": 1
                    }
                  ]
                }
                """;
    }

    private static RequestPostProcessor jwtWithOrganizationAndPermission(
            String permission
    ) {
        return jwt()
                .jwt(jwt -> jwt.claim("organizationId", 10L))
                .authorities(() -> permission);
    }

    private static SectionResponse response(Long id, Long gradeLevelId) {
        return new SectionResponse(
                id,
                20L,
                gradeLevelId,
                "A",
                "Section A",
                1,
                0L,
                OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubSectionService sectionService() {
            return new StubSectionService();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("test-user")
                    .build();
        }
    }

    static class StubSectionService implements SectionService {

        private List<SectionResponse> responses = List.of();
        private long lastOrganizationId;
        private long lastAcademicYearId;

        @Override
        public SectionResponse create(
                long organizationId,
                long academicYearId,
                long gradeLevelId,
                CreateSectionRequest request
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SectionResponse> createBulk(
                long organizationId,
                long academicYearId,
                long gradeLevelId,
                BulkCreateSectionsRequest request
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SectionResponse> applyStructure(
                long organizationId,
                long academicYearId,
                ApplySectionStructureRequest request
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            return responses;
        }

        @Override
        public SectionResponse getById(
                long organizationId,
                long academicYearId,
                long gradeLevelId,
                long sectionId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SectionResponse> getAll(
                long organizationId,
                long academicYearId,
                long gradeLevelId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SectionResponse update(
                long organizationId,
                long academicYearId,
                long gradeLevelId,
                long sectionId,
                UpdateSectionRequest request
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
