package com.dawnrise.academic.studentattendance.importing.controller;

import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.config.SecurityConfig;
import com.dawnrise.academic.security.AcademicTenantSecurity;
import com.dawnrise.academic.studentattendance.importing.dto.*;
import com.dawnrise.academic.studentattendance.importing.enums.StudentAttendanceImportFormat;
import com.dawnrise.academic.studentattendance.importing.service.StudentAttendanceImportService;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StudentAttendanceImportController.class)
@Import({
        SecurityConfig.class,
        AcademicTenantSecurity.class,
        GlobalExceptionHandler.class,
        StudentAttendanceImportControllerTest.TestConfig.class
})
class StudentAttendanceImportControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired StubService service;

    @Test
    void authorizedActorCanDownloadTemplateAndPreviewFile() throws Exception {
        service.previewResponse = previewResponse();
        MockMultipartFile file = new MockMultipartFile(
                "file", "attendance.csv", "text/csv", "headers".getBytes()
        );

        mockMvc.perform(get("/api/v1/student-attendance/imports/template.csv")
                        .with(actor()))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=student-attendance-import-template.csv"
                ));

        mockMvc.perform(multipart("/api/v1/student-attendance/imports/preview")
                        .file(file)
                        .with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewId").value(5));

        assertThat(service.organizationId).isEqualTo(7L);
        assertThat(service.actorUserId).isEqualTo(11L);
    }

    @Test
    void confirmUsesFingerprintAndIdempotencyKey() throws Exception {
        service.confirmResponse = new StudentAttendanceOfflineSyncResponse(
                9L, "import-1", "sha256:" + "b".repeat(64),
                false, null, List.of()
        );

        mockMvc.perform(post("/api/v1/student-attendance/imports/5/confirm")
                        .with(actor())
                        .header("Idempotency-Key", "import-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"previewFingerprint":"sha256:%s"}
                                """.formatted("a".repeat(64))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operationId").value(9));

        assertThat(service.previewId).isEqualTo(5L);
        assertThat(service.idempotencyKey).isEqualTo("import-1");
    }

    @Test
    void missingPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/student-attendance/imports/template.csv")
                        .with(jwt().jwt(builder -> builder
                                .subject("11")
                                .claim("organizationId", 7L))))
                .andExpect(status().isForbidden());
    }

    private RequestPostProcessor actor() {
        return jwt()
                .jwt(builder -> builder
                        .subject("11")
                        .claim("organizationId", 7L))
                .authorities(() -> "STUDENT_ATTENDANCE_RECORD");
    }

    private StudentAttendanceImportPreviewResponse previewResponse() {
        return new StudentAttendanceImportPreviewResponse(
                5L, "sha256:" + "a".repeat(64), "attendance.csv",
                StudentAttendanceImportFormat.CSV, true,
                3L, 3L, 3L, LocalDate.of(2026, 9, 11), 0L,
                1, OffsetDateTime.now().plusMinutes(30), List.of(), List.of()
        );
    }

    @TestConfiguration
    static class TestConfig {
        @Bean JwtDecoder jwtDecoder() { return token -> null; }
        @Bean StubService studentAttendanceImportService() { return new StubService(); }
    }

    static class StubService implements StudentAttendanceImportService {
        long organizationId;
        long actorUserId;
        long previewId;
        String idempotencyKey;
        StudentAttendanceImportPreviewResponse previewResponse;
        StudentAttendanceOfflineSyncResponse confirmResponse;

        @Override public byte[] csvTemplate() { return "header\r\n".getBytes(); }

        @Override
        public StudentAttendanceImportPreviewResponse preview(
                long organizationId, long actorUserId, MultipartFile file) {
            this.organizationId = organizationId;
            this.actorUserId = actorUserId;
            return previewResponse;
        }

        @Override
        public StudentAttendanceOfflineSyncResponse confirm(
                long organizationId,
                long actorUserId,
                long previewId,
                String idempotencyKey,
                ConfirmStudentAttendanceImportRequest request
        ) {
            this.organizationId = organizationId;
            this.actorUserId = actorUserId;
            this.previewId = previewId;
            this.idempotencyKey = idempotencyKey;
            return confirmResponse;
        }
    }
}
