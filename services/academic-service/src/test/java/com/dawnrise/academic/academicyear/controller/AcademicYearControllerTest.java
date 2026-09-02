package com.dawnrise.academic.academicyear.controller;

import com.dawnrise.academic.academicyear.dto.AcademicYearResponse;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.service.AcademicYearService;
import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.config.SecurityConfig;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AcademicYearController.class)
@Import({
        SecurityConfig.class,
        AcademicTenantSecurity.class,
        GlobalExceptionHandler.class,
        AcademicYearControllerTest.TestConfig.class
})
class AcademicYearControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubAcademicYearService academicYearService;

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrganizationIdIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years")
                        .with(jwt().authorities(() -> "ACADEMIC_YEAR_VIEW")))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingRequiredPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years")
                        .with(jwt().jwt(jwt -> jwt.claim("organizationId", 10L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void correctPermissionAndOrganizationIdAllowCreate() throws Exception {
        academicYearService.response = response(1L, AcademicYearStatus.PLANNED);

        mockMvc.perform(post("/api/v1/academic-years")
                        .with(jwtWithOrganizationAndPermission("ACADEMIC_YEAR_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": 999,
                                  "name": "2026-2027",
                                  "startDate": "2026-04-01",
                                  "endDate": "2027-03-31"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        assertThat(academicYearService.lastOrganizationId).isEqualTo(10L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowGetAll() throws Exception {
        academicYearService.responses = List.of(response(1L, AcademicYearStatus.PLANNED));

        mockMvc.perform(get("/api/v1/academic-years")
                        .with(jwtWithOrganizationAndPermission("ACADEMIC_YEAR_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));

        assertThat(academicYearService.lastOrganizationId).isEqualTo(10L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowGetActive() throws Exception {
        academicYearService.response = response(1L, AcademicYearStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/academic-years/active")
                        .with(jwtWithOrganizationAndPermission("ACADEMIC_YEAR_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(academicYearService.lastOrganizationId).isEqualTo(10L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowGetById() throws Exception {
        academicYearService.response = response(1L, AcademicYearStatus.PLANNED);

        mockMvc.perform(get("/api/v1/academic-years/1")
                        .with(jwtWithOrganizationAndPermission("ACADEMIC_YEAR_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        assertThat(academicYearService.lastOrganizationId).isEqualTo(10L);
        assertThat(academicYearService.lastAcademicYearId).isEqualTo(1L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowUpdate() throws Exception {
        academicYearService.response = response(1L, AcademicYearStatus.PLANNED);

        mockMvc.perform(put("/api/v1/academic-years/1")
                        .with(jwtWithOrganizationAndPermission("ACADEMIC_YEAR_UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "2026-2027",
                                  "startDate": "2026-04-01",
                                  "endDate": "2027-03-31",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        assertThat(academicYearService.lastOrganizationId).isEqualTo(10L);
        assertThat(academicYearService.lastAcademicYearId).isEqualTo(1L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowActivate() throws Exception {
        academicYearService.response = response(1L, AcademicYearStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/academic-years/1/activate")
                        .with(jwtWithOrganizationAndPermission("ACADEMIC_YEAR_ACTIVATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(academicYearService.lastOrganizationId).isEqualTo(10L);
        assertThat(academicYearService.lastAcademicYearId).isEqualTo(1L);
    }

    @Test
    void correctPermissionAndOrganizationIdAllowClose() throws Exception {
        academicYearService.response = response(1L, AcademicYearStatus.CLOSED);

        mockMvc.perform(post("/api/v1/academic-years/1/close")
                        .with(jwtWithOrganizationAndPermission("ACADEMIC_YEAR_CLOSE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertThat(academicYearService.lastOrganizationId).isEqualTo(10L);
        assertThat(academicYearService.lastAcademicYearId).isEqualTo(1L);
    }

    @Test
    void invalidRequestBodiesReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years")
                        .with(jwtWithOrganizationAndPermission("ACADEMIC_YEAR_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "startDate": null,
                                  "endDate": "2027-03-31"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.name")
                        .value("Academic year name is required"))
                .andExpect(jsonPath("$.validationErrors.startDate")
                        .value("Start date is required"));
    }

    @Test
    void invalidIdsReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/0")
                        .with(jwtWithOrganizationAndPermission("ACADEMIC_YEAR_VIEW")))
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

    private static AcademicYearResponse response(
            Long id,
            AcademicYearStatus status
    ) {
        return new AcademicYearResponse(
                id,
                "2026-2027",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2027, 3, 31),
                status,
                0L,
                OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubAcademicYearService academicYearService() {
            return new StubAcademicYearService();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("test-user")
                    .build();
        }
    }

    static class StubAcademicYearService implements AcademicYearService {

        private AcademicYearResponse response;
        private List<AcademicYearResponse> responses = List.of();
        private long lastOrganizationId;
        private long lastAcademicYearId;

        @Override
        public AcademicYearResponse create(
                long organizationId,
                com.dawnrise.academic.academicyear.dto.CreateAcademicYearRequest request
        ) {
            lastOrganizationId = organizationId;
            return response;
        }

        @Override
        public AcademicYearResponse getById(
                long organizationId,
                long academicYearId
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            return response;
        }

        @Override
        public List<AcademicYearResponse> getAll(long organizationId) {
            lastOrganizationId = organizationId;
            return responses;
        }

        @Override
        public AcademicYearResponse getActive(long organizationId) {
            lastOrganizationId = organizationId;
            return response;
        }

        @Override
        public AcademicYearResponse update(
                long organizationId,
                long academicYearId,
                com.dawnrise.academic.academicyear.dto.UpdateAcademicYearRequest request
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            return response;
        }

        @Override
        public AcademicYearResponse activate(
                long organizationId,
                long academicYearId
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            return response;
        }

        @Override
        public AcademicYearResponse close(
                long organizationId,
                long academicYearId
        ) {
            lastOrganizationId = organizationId;
            lastAcademicYearId = academicYearId;
            return response;
        }
    }
}
