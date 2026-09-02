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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SectionController.class)
@Import({
        SecurityConfig.class,
        AcademicTenantSecurity.class,
        GlobalExceptionHandler.class,
        SectionControllerTest.TestConfig.class
})
class SectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubSectionService sectionService;

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/sections"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrganizationIdIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/sections")
                        .with(jwt().authorities(() -> "SECTION_VIEW")))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingRequiredPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/sections")
                        .with(jwt().jwt(jwt -> jwt.claim("organizationId", 10L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void correctPermissionAndOrganizationIdAllowCreate() throws Exception {
        sectionService.response = response(40L);

        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/30/sections")
                        .with(jwtWithOrganizationAndPermission("SECTION_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "A",
                                  "name": "Section A",
                                  "displayOrder": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(40))
                .andExpect(jsonPath("$.code").value("A"));

        assertThat(sectionService.lastOrganizationId).isEqualTo(10L);
        assertThat(sectionService.lastAcademicYearId).isEqualTo(20L);
        assertThat(sectionService.lastGradeLevelId).isEqualTo(30L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowBulkCreate() throws Exception {
        sectionService.responses = List.of(response(40L), response(41L));

        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/30/sections/bulk")
                        .with(jwtWithOrganizationAndPermission("SECTION_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sections": [
                                    {
                                      "code": "A",
                                      "name": "Section A",
                                      "displayOrder": 1
                                    },
                                    {
                                      "code": "B",
                                      "name": "Section B",
                                      "displayOrder": 2
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(40))
                .andExpect(jsonPath("$[1].id").value(41));

        assertThat(sectionService.lastOrganizationId).isEqualTo(10L);
        assertThat(sectionService.lastAcademicYearId).isEqualTo(20L);
        assertThat(sectionService.lastGradeLevelId).isEqualTo(30L);
    }

    @Test
    void bulkCreateRequiresSectionCreatePermission() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/30/sections/bulk")
                        .with(jwtWithOrganizationAndPermission("SECTION_VIEW"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sections": [
                                    {
                                      "code": "A",
                                      "name": "Section A",
                                      "displayOrder": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void correctPermissionAndOrganizationIdAllowGetAll() throws Exception {
        sectionService.responses = List.of(response(40L));

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/sections")
                        .with(jwtWithOrganizationAndPermission("SECTION_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(40));

        assertThat(sectionService.lastOrganizationId).isEqualTo(10L);
        assertThat(sectionService.lastAcademicYearId).isEqualTo(20L);
        assertThat(sectionService.lastGradeLevelId).isEqualTo(30L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowGetById() throws Exception {
        sectionService.response = response(40L);

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/sections/40")
                        .with(jwtWithOrganizationAndPermission("SECTION_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(40));

        assertThat(sectionService.lastOrganizationId).isEqualTo(10L);
        assertThat(sectionService.lastAcademicYearId).isEqualTo(20L);
        assertThat(sectionService.lastGradeLevelId).isEqualTo(30L);
        assertThat(sectionService.lastSectionId).isEqualTo(40L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowUpdate() throws Exception {
        sectionService.response = response(40L);

        mockMvc.perform(put("/api/v1/academic-years/20/grade-levels/30/sections/40")
                        .with(jwtWithOrganizationAndPermission("SECTION_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "A",
                                  "name": "Section A",
                                  "displayOrder": 1,
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(40));

        assertThat(sectionService.lastOrganizationId).isEqualTo(10L);
        assertThat(sectionService.lastAcademicYearId).isEqualTo(20L);
        assertThat(sectionService.lastGradeLevelId).isEqualTo(30L);
        assertThat(sectionService.lastSectionId).isEqualTo(40L);
    }

    @Test
    void invalidRequestBodiesReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/30/sections")
                        .with(jwtWithOrganizationAndPermission("SECTION_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "",
                                  "name": "",
                                  "displayOrder": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.code")
                        .value("Section code is required"))
                .andExpect(jsonPath("$.validationErrors.name")
                        .value("Section name is required"))
                .andExpect(jsonPath("$.validationErrors.displayOrder")
                        .value("Display order must be greater than zero"));
    }

    @Test
    void invalidBulkCreateRequestBodiesReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/30/sections/bulk")
                        .with(jwtWithOrganizationAndPermission("SECTION_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sections": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.sections")
                        .value("At least one section is required"));

        mockMvc.perform(post("/api/v1/academic-years/20/grade-levels/30/sections/bulk")
                        .with(jwtWithOrganizationAndPermission("SECTION_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
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
                        .value("A maximum of 20 sections can be created at once"));
    }

    @Test
    void invalidPathIdsReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/0/grade-levels/30/sections")
                        .with(jwtWithOrganizationAndPermission("SECTION_VIEW")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/0/sections")
                        .with(jwtWithOrganizationAndPermission("SECTION_VIEW")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        mockMvc.perform(get("/api/v1/academic-years/20/grade-levels/30/sections/0")
                        .with(jwtWithOrganizationAndPermission("SECTION_VIEW")))
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

    private static SectionResponse response(Long id) {
        return new SectionResponse(
                id,
                20L,
                30L,
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

        private SectionResponse response;
        private List<SectionResponse> responses = List.of();
        private long lastOrganizationId;
        private long lastAcademicYearId;
        private long lastGradeLevelId;
        private long lastSectionId;

        @Override
        public SectionResponse create(
                long organizationId,
                long academicYearId,
                long gradeLevelId,
                CreateSectionRequest request
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            return response;
        }

        @Override
        public SectionResponse getById(
                long organizationId,
                long academicYearId,
                long gradeLevelId,
                long sectionId
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            lastSectionId = sectionId;
            return response;
        }

        @Override
        public List<SectionResponse> getAll(
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
        public List<SectionResponse> createBulk(
                long organizationId,
                long academicYearId,
                long gradeLevelId,
                BulkCreateSectionsRequest request
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            return responses;
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
        public SectionResponse update(
                long organizationId,
                long academicYearId,
                long gradeLevelId,
                long sectionId,
                UpdateSectionRequest request
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastGradeLevelId = gradeLevelId;
            lastSectionId = sectionId;
            return response;
        }
    }
}
