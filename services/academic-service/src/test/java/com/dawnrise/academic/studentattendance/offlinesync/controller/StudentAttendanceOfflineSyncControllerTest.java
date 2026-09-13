package com.dawnrise.academic.studentattendance.offlinesync.controller;

import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.config.SecurityConfig;
import com.dawnrise.academic.security.AcademicTenantSecurity;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncRequest;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncResponse;
import com.dawnrise.academic.studentattendance.offlinesync.service.StudentAttendanceOfflineSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentAttendanceOfflineSyncController.class)
@Import({
        SecurityConfig.class,
        AcademicTenantSecurity.class,
        GlobalExceptionHandler.class,
        StudentAttendanceOfflineSyncControllerTest.TestConfig.class
})
class StudentAttendanceOfflineSyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubService service;

    @Test
    void recordAuthorityCanSynchronizeUsingJwtTenantAndActor() throws Exception {
        service.response = new StudentAttendanceOfflineSyncResponse(
                9L, "offline-1", "sha256:" + "a".repeat(64),
                false, null, List.of()
        );

        mockMvc.perform(post("/api/v1/student-attendance/offline-sync")
                        .with(jwt().jwt(builder -> builder
                                .subject("11")
                                .claim("organizationId", 7L)
                                .claim("permissions", List.of("STUDENT_ATTENDANCE_RECORD"))))
                        .header("Idempotency-Key", "offline-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operationId").value(9));

        assertThat(service.organizationId).isEqualTo(7L);
        assertThat(service.actorUserId).isEqualTo(11L);
        assertThat(service.idempotencyKey).isEqualTo("offline-1");
    }

    @Test
    void replayReturnsOkAndMissingAuthorityIsForbidden() throws Exception {
        service.response = new StudentAttendanceOfflineSyncResponse(
                9L, "offline-1", "sha256:" + "a".repeat(64),
                true, null, List.of()
        );
        mockMvc.perform(post("/api/v1/student-attendance/offline-sync")
                        .with(jwt().jwt(builder -> builder
                                .subject("11")
                                .claim("organizationId", 7L)
                                .claim("permissions", List.of("STUDENT_ATTENDANCE_RECORD"))))
                        .header("Idempotency-Key", "offline-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/student-attendance/offline-sync")
                        .with(jwt().jwt(builder -> builder
                                .subject("11")
                                .claim("organizationId", 7L)))
                        .header("Idempotency-Key", "offline-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json()))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestValidationRejectsEmptyRecords() throws Exception {
        mockMvc.perform(post("/api/v1/student-attendance/offline-sync")
                        .with(jwt().jwt(builder -> builder
                                .subject("11")
                                .claim("organizationId", 7L)
                                .claim("permissions", List.of("STUDENT_ATTENDANCE_RECORD"))))
                        .header("Idempotency-Key", "offline-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyRecordsJson()))
                .andExpect(status().isBadRequest());
    }

    private String emptyRecordsJson() {
        return """
                {
                  "academicYearId": 3,
                  "gradeLevelId": 3,
                  "sectionId": 3,
                  "attendanceDate": "2026-09-11",
                  "baseSessionVersion": 0,
                  "syncMode": "PARTIAL",
                  "expectedRosterEnrollmentIds": [3],
                  "records": []
                }
                """;
    }

    private String json() {
        return """
                {
                  "academicYearId": 3,
                  "gradeLevelId": 3,
                  "sectionId": 3,
                  "attendanceDate": "2026-09-11",
                  "baseSessionVersion": 0,
                  "syncMode": "PARTIAL",
                  "expectedRosterEnrollmentIds": [3],
                  "records": [{
                    "studentEnrollmentId": 3,
                    "expectedRecordVersion": 0,
                    "recordedStatus": "PRESENT",
                    "remarks": "Present"
                  }]
                }
                """;
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> null;
        }

        @Bean
        StubService studentAttendanceOfflineSyncService() {
            return new StubService();
        }
    }

    static class StubService implements StudentAttendanceOfflineSyncService {
        long organizationId;
        long actorUserId;
        String idempotencyKey;
        StudentAttendanceOfflineSyncResponse response;

        @Override
        public StudentAttendanceOfflineSyncResponse synchronize(
                long organizationId,
                long actorUserId,
                String idempotencyKey,
                StudentAttendanceOfflineSyncRequest request
        ) {
            this.organizationId = organizationId;
            this.actorUserId = actorUserId;
            this.idempotencyKey = idempotencyKey;
            return response;
        }
    }
}
