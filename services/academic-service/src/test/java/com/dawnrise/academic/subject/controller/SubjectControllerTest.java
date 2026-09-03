package com.dawnrise.academic.subject.controller;

import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.config.SecurityConfig;
import com.dawnrise.academic.security.AcademicTenantSecurity;
import com.dawnrise.academic.subject.dto.CreateSubjectRequest;
import com.dawnrise.academic.subject.dto.SubjectResponse;
import com.dawnrise.academic.subject.dto.UpdateSubjectRequest;
import com.dawnrise.academic.subject.service.SubjectService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubjectController.class)
@Import({
        SecurityConfig.class,
        AcademicTenantSecurity.class,
        GlobalExceptionHandler.class,
        SubjectControllerTest.TestConfig.class
})
class SubjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubSubjectService subjectService;

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/20/subjects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrganizationIdIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/20/subjects")
                        .with(jwt().authorities(() -> "SUBJECT_VIEW")))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingRequiredPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/20/subjects")
                        .with(jwt().jwt(jwt -> jwt.claim("organizationId", 10L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void correctPermissionAndOrganizationIdAllowCreate() throws Exception {
        subjectService.response = response(30L);

        mockMvc.perform(post("/api/v1/academic-years/20/subjects")
                        .with(jwtWithOrganizationAndPermission("SUBJECT_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "MATH",
                                  "name": "Mathematics",
                                  "description": "Core subject"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.code").value("MATH"));

        assertThat(subjectService.lastOrganizationId).isEqualTo(10L);
        assertThat(subjectService.lastAcademicYearId).isEqualTo(20L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowGetAll() throws Exception {
        subjectService.responses = List.of(response(30L));

        mockMvc.perform(get("/api/v1/academic-years/20/subjects")
                        .with(jwtWithOrganizationAndPermission("SUBJECT_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(30));

        assertThat(subjectService.lastOrganizationId).isEqualTo(10L);
        assertThat(subjectService.lastAcademicYearId).isEqualTo(20L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowGetById() throws Exception {
        subjectService.response = response(30L);

        mockMvc.perform(get("/api/v1/academic-years/20/subjects/30")
                        .with(jwtWithOrganizationAndPermission("SUBJECT_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(30));

        assertThat(subjectService.lastOrganizationId).isEqualTo(10L);
        assertThat(subjectService.lastAcademicYearId).isEqualTo(20L);
        assertThat(subjectService.lastSubjectId).isEqualTo(30L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowUpdate() throws Exception {
        subjectService.response = response(30L);

        mockMvc.perform(put("/api/v1/academic-years/20/subjects/30")
                        .with(jwtWithOrganizationAndPermission("SUBJECT_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "MATH",
                                  "name": "Mathematics",
                                  "description": null,
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(30));

        assertThat(subjectService.lastOrganizationId).isEqualTo(10L);
        assertThat(subjectService.lastAcademicYearId).isEqualTo(20L);
        assertThat(subjectService.lastSubjectId).isEqualTo(30L);
    }

    @Test
    void routesRequireCorrectPermissions() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/subjects")
                        .with(jwtWithOrganizationAndPermission("SUBJECT_VIEW"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/academic-years/20/subjects/30")
                        .with(jwtWithOrganizationAndPermission("SUBJECT_VIEW"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateJson()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/academic-years/20/subjects")
                        .with(jwtWithOrganizationAndPermission("SUBJECT_CREATE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidRequestBodiesReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/subjects")
                        .with(jwtWithOrganizationAndPermission("SUBJECT_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "",
                                  "name": "",
                                  "description": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.code")
                        .value("Subject code is required"))
                .andExpect(jsonPath("$.validationErrors.name")
                        .value("Subject name is required"))
                .andExpect(jsonPath("$.validationErrors.description")
                        .value("Subject description cannot be blank"));

        mockMvc.perform(put("/api/v1/academic-years/20/subjects/30")
                        .with(jwtWithOrganizationAndPermission("SUBJECT_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "MATH",
                                  "name": "Mathematics",
                                  "description": null,
                                  "version": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.version")
                        .value("Version is required"));
    }

    @Test
    void oversizedRequestValuesReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/20/subjects")
                        .with(jwtWithOrganizationAndPermission("SUBJECT_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "%s",
                                  "description": "%s"
                                }
                                """.formatted(
                                "a".repeat(51),
                                "a".repeat(121),
                                "a".repeat(501)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.code")
                        .value("Subject code cannot exceed 50 characters"))
                .andExpect(jsonPath("$.validationErrors.name")
                        .value("Subject name cannot exceed 120 characters"))
                .andExpect(jsonPath("$.validationErrors.description")
                        .value("Subject description cannot exceed 500 characters"));
    }

    @Test
    void invalidPathIdsReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/0/subjects")
                        .with(jwtWithOrganizationAndPermission("SUBJECT_VIEW")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        mockMvc.perform(get("/api/v1/academic-years/20/subjects/0")
                        .with(jwtWithOrganizationAndPermission("SUBJECT_VIEW")))
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
                  "code": "MATH",
                  "name": "Mathematics",
                  "description": null
                }
                """;
    }

    private static String validUpdateJson() {
        return """
                {
                  "code": "MATH",
                  "name": "Mathematics",
                  "description": null,
                  "version": 0
                }
                """;
    }

    private static SubjectResponse response(Long id) {
        return new SubjectResponse(
                id,
                20L,
                "MATH",
                "Mathematics",
                "Core subject",
                0L,
                OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubSubjectService subjectService() {
            return new StubSubjectService();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("test-user")
                    .build();
        }
    }

    static class StubSubjectService implements SubjectService {

        private SubjectResponse response;
        private List<SubjectResponse> responses = List.of();
        private long lastOrganizationId;
        private long lastAcademicYearId;
        private long lastSubjectId;

        @Override
        public SubjectResponse create(
                long organizationId,
                long academicYearId,
                CreateSubjectRequest request
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            return response;
        }

        @Override
        public SubjectResponse getById(
                long organizationId,
                long academicYearId,
                long subjectId
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastSubjectId = subjectId;
            return response;
        }

        @Override
        public List<SubjectResponse> getAll(
                long organizationId,
                long academicYearId
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            return responses;
        }

        @Override
        public SubjectResponse update(
                long organizationId,
                long academicYearId,
                long subjectId,
                UpdateSubjectRequest request
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            lastSubjectId = subjectId;
            return response;
        }
    }
}
