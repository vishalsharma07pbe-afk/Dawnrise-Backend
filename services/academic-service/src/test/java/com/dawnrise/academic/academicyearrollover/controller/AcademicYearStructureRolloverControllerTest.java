package com.dawnrise.academic.academicyearrollover.controller;

import com.dawnrise.academic.academicyearrollover.dto.*;
import com.dawnrise.academic.academicyearrollover.service.AcademicYearStructureRolloverService;
import com.dawnrise.academic.academicyearrollover.service.RolloverConfirmation;
import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.config.SecurityConfig;
import com.dawnrise.academic.security.AcademicTenantSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AcademicYearStructureRolloverController.class)
@Import({
        SecurityConfig.class,
        AcademicTenantSecurity.class,
        GlobalExceptionHandler.class,
        AcademicYearStructureRolloverControllerTest.TestConfig.class
})
class AcademicYearStructureRolloverControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubRolloverService rolloverService;

    @Test
    void previewRequiresRolloverPermission() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/2/structure-rollovers/preview")
                        .with(jwt().jwt(jwt -> jwt.claim("organizationId", 10L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAcademicYearId\":1,\"includeGradeLevels\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void previewReturnsOkWithConflicts() throws Exception {
        rolloverService.previewResponse =
                new AcademicYearStructureRolloverPreviewResponse(
                        1L,
                        2L,
                        false,
                        hash('a'),
                        hash('b'),
                        new RolloverCountsResponse(1, 0, 0, 0, 1),
                        new RolloverProposedRecordsResponse(
                                List.of(new ProposedGradeLevelResponse(
                                        11L,
                                        "G1",
                                        "Grade 1",
                                        1
                                )),
                                List.of(),
                                List.of(),
                                List.of()
                        ),
                        List.of(new RolloverConflictResponse(
                                "TARGET_GRADE_LEVEL_CODE_EXISTS",
                                "GRADE_LEVEL",
                                11L,
                                "code",
                                "G1",
                                "Target conflict"
                        ))
                );

        mockMvc.perform(post("/api/v1/academic-years/2/structure-rollovers/preview")
                        .with(jwtWithPermission())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAcademicYearId\":1,\"includeGradeLevels\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canConfirm").value(false))
                .andExpect(jsonPath("$.conflicts[0].type")
                        .value("TARGET_GRADE_LEVEL_CODE_EXISTS"));

        assertThat(rolloverService.lastOrganizationId).isEqualTo(10L);
    }

    @Test
    void confirmRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/academic-years/2/structure-rollovers")
                        .with(jwtWithPermission())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceAcademicYearId": 1,
                                  "includeGradeLevels": true,
                                  "previewFingerprint": "%s"
                                }
                                """.formatted(hash('b'))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmReturnsCreatedForFirstSuccess() throws Exception {
        rolloverService.confirmation = new RolloverConfirmation(
                result(100L),
                false
        );

        mockMvc.perform(post("/api/v1/academic-years/2/structure-rollovers")
                        .with(jwtWithPermission())
                        .header("Idempotency-Key", "rollover-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceAcademicYearId": 1,
                                  "includeGradeLevels": true,
                                  "previewFingerprint": "%s"
                                }
                                """.formatted(hash('b'))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operationId").value(100));
    }

    @Test
    void confirmReturnsOkForReplay() throws Exception {
        rolloverService.confirmation = new RolloverConfirmation(
                result(100L),
                true
        );

        mockMvc.perform(post("/api/v1/academic-years/2/structure-rollovers")
                        .with(jwtWithPermission())
                        .header("Idempotency-Key", "rollover-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceAcademicYearId": 1,
                                  "includeGradeLevels": true,
                                  "previewFingerprint": "%s"
                                }
                                """.formatted(hash('b'))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(100));
    }

    @Test
    void lookupUsesTenantScopedService() throws Exception {
        rolloverService.operationResponse =
                new AcademicYearStructureRolloverOperationResponse(
                        100L,
                        "SUCCEEDED",
                        1L,
                        2L,
                        hash('a'),
                        hash('b'),
                        null,
                        null,
                        new RolloverCountsResponse(1, 0, 0, 0, 1),
                        new RolloverMappingsResponse(
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()
                        ),
                        null,
                        null,
                        null,
                        null
                );

        mockMvc.perform(get("/api/v1/academic-year-structure-rollovers/100")
                        .with(jwtWithPermission()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(100));

        assertThat(rolloverService.lastOperationId).isEqualTo(100L);
    }

    private static AcademicYearStructureRolloverResultResponse result(
            Long operationId
    ) {
        return new AcademicYearStructureRolloverResultResponse(
                operationId,
                "SUCCEEDED",
                1L,
                2L,
                hash('a'),
                hash('b'),
                new RolloverCountsResponse(1, 0, 0, 0, 1),
                new RolloverMappingsResponse(
                        List.of(new RolloverIdMappingResponse(11L, 21L)),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );
    }

    private static String hash(char value) {
        return "sha256:" + String.valueOf(value).repeat(64);
    }

    private static RequestPostProcessor jwtWithPermission() {
        return jwt().jwt(jwt -> jwt
                .subject("42")
                .claim("organizationId", 10L))
                .authorities(() -> "ACADEMIC_YEAR_STRUCTURE_ROLLOVER");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> null;
        }

        @Bean
        StubRolloverService rolloverService() {
            return new StubRolloverService();
        }
    }

    static class StubRolloverService
            implements AcademicYearStructureRolloverService {

        long lastOrganizationId;
        long lastOperationId;
        AcademicYearStructureRolloverPreviewResponse previewResponse;
        RolloverConfirmation confirmation;
        AcademicYearStructureRolloverOperationResponse operationResponse;

        @Override
        public AcademicYearStructureRolloverPreviewResponse preview(
                long organizationId,
                long targetAcademicYearId,
                AcademicYearStructureRolloverRequest request
        ) {
            this.lastOrganizationId = organizationId;
            return previewResponse;
        }

        @Override
        public RolloverConfirmation confirm(
                long organizationId,
                long authenticatedUserId,
                long targetAcademicYearId,
                String idempotencyKey,
                ConfirmAcademicYearStructureRolloverRequest request
        ) {
            this.lastOrganizationId = organizationId;
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new com.dawnrise.academic.academicyearrollover.exception.InvalidRolloverRequestException(
                        "Idempotency-Key header is required"
                );
            }
            return confirmation;
        }

        @Override
        public AcademicYearStructureRolloverOperationResponse getOperation(
                long organizationId,
                long operationId
        ) {
            this.lastOrganizationId = organizationId;
            this.lastOperationId = operationId;
            return operationResponse;
        }
    }
}
