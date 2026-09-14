package com.dawnrise.academic.studentattendance.recording.controller;

import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.config.SecurityConfig;
import com.dawnrise.academic.security.AcademicTenantSecurity;
import com.dawnrise.academic.studentattendance.discovery.dto.AccessibleStudentAttendanceSectionResponse;
import com.dawnrise.academic.studentattendance.discovery.service.StudentAttendanceAccessibleSectionService;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineDraftRosterEntry;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineDraftSnapshot;
import com.dawnrise.academic.studentattendance.recording.dto.BulkStudentAttendanceRecordRequest;
import com.dawnrise.academic.studentattendance.recording.dto.StudentAttendanceSessionResponse;
import com.dawnrise.academic.studentattendance.recording.dto.SubmitStudentAttendanceRequest;
import com.dawnrise.academic.studentattendance.recording.exception.StudentAttendanceRecordingConflictException;
import com.dawnrise.academic.studentattendance.recording.service.StudentAttendanceRecordingService;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentAttendanceRecordingController.class)
@Import({
        SecurityConfig.class,
        AcademicTenantSecurity.class,
        GlobalExceptionHandler.class,
        StudentAttendanceRecordingControllerTest.TestConfig.class
})
class StudentAttendanceRecordingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubRecordingService recordingService;

    @Autowired
    private StubAccessibleSectionService accessibleSectionService;

    @BeforeEach
    void resetStubs() {
        recordingService.reset();
        accessibleSectionService.reset();
    }

    @Test
    void accessibleSectionsUsesJwtTenantAndActor() throws Exception {
        accessibleSectionService.responses = List.of(
                new AccessibleStudentAttendanceSectionResponse(
                        3L, "2026-2027", 4L, "CLASS_1", "Class 1",
                        5L, "A", "A"
                )
        );

        mockMvc.perform(get("/api/v1/student-attendance/accessible-sections")
                        .with(jwtWithOrganizationUserAndRecordPermission()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].academicYearId").value(3))
                .andExpect(jsonPath("$[0].gradeLevelCode").value("CLASS_1"))
                .andExpect(jsonPath("$[0].sectionCode").value("A"));

        assertThat(accessibleSectionService.organizationId).isEqualTo(7L);
        assertThat(accessibleSectionService.actorUserId).isEqualTo(11L);
    }

    @Test
    void rosterUsesJwtTenantAndActorAndReturnsStudentUserId() throws Exception {
        recordingService.snapshot = new StudentAttendanceOfflineDraftSnapshot(
                2L,
                List.of(new StudentAttendanceOfflineDraftRosterEntry(
                        31L, 41L, "DR-001", 4L
                ))
        );

        mockMvc.perform(get("/api/v1/student-attendance/academic-years/3/grades/4/sections/5/dates/2026-09-11/roster")
                        .with(jwtWithOrganizationUserAndRecordPermission()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseSessionVersion").value(2))
                .andExpect(jsonPath("$.roster[0].studentEnrollmentId").value(31))
                .andExpect(jsonPath("$.roster[0].studentUserId").value(41))
                .andExpect(jsonPath("$.roster[0].existingRecordVersion").value(4));

        assertThat(recordingService.organizationId).isEqualTo(7L);
        assertThat(recordingService.actorUserId).isEqualTo(11L);
        assertThat(recordingService.academicYearId).isEqualTo(3L);
        assertThat(recordingService.gradeLevelId).isEqualTo(4L);
        assertThat(recordingService.sectionId).isEqualTo(5L);
        assertThat(recordingService.attendanceDate)
                .isEqualTo(LocalDate.of(2026, 9, 11));
    }

    @Test
    void missingPermissionAndOrganizationAreForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/student-attendance/accessible-sections")
                        .with(jwt().jwt(builder -> builder
                                .subject("11")
                                .claim("organizationId", 7L))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/student-attendance/accessible-sections")
                        .with(jwt().jwt(builder -> builder.subject("11"))
                                .authorities(() -> "STUDENT_ATTENDANCE_RECORD")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/student-attendance/academic-years/3/grades/4/sections/5/dates/2026-09-11/roster")
                        .with(jwt().jwt(builder -> builder
                                .subject("11")
                                .claim("organizationId", 7L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPathVariablesAreBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/student-attendance/academic-years/0/grades/4/sections/5/dates/2026-09-11/roster")
                        .with(jwtWithOrganizationUserAndRecordPermission()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/student-attendance/academic-years/3/grades/4/sections/5/dates/not-a-date/roster")
                        .with(jwtWithOrganizationUserAndRecordPermission()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saveDraftAcceptsExpectedVersions() throws Exception {
        mockMvc.perform(put("/api/v1/student-attendance/sessions/99/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedSessionVersion": 2,
                                  "records": [
                                    {
                                      "studentEnrollmentId": 31,
                                      "expectedRecordVersion": 4,
                                      "recordedStatus": "PRESENT",
                                      "remarks": "Checked"
                                    }
                                  ]
                                }
                                """)
                        .with(jwtWithOrganizationUserAndRecordPermission()))
                .andExpect(status().isOk());

        assertThat(recordingService.sessionId).isEqualTo(99L);
        assertThat(recordingService.saveRequest.expectedSessionVersion())
                .isEqualTo(2L);
        assertThat(recordingService.saveRequest.records().get(0)
                .expectedRecordVersion()).isEqualTo(4L);
    }

    @Test
    void saveDraftValidationFailuresAreBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/student-attendance/sessions/99/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "records": [
                                    {
                                      "studentEnrollmentId": 31,
                                      "expectedRecordVersion": 0,
                                      "recordedStatus": "PRESENT"
                                    }
                                  ]
                                }
                                """)
                        .with(jwtWithOrganizationUserAndRecordPermission()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/student-attendance/sessions/99/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedSessionVersion": 0,
                                  "records": [
                                    {
                                      "studentEnrollmentId": 31,
                                      "expectedRecordVersion": -1,
                                      "recordedStatus": "PRESENT"
                                    }
                                  ]
                                }
                                """)
                        .with(jwtWithOrganizationUserAndRecordPermission()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saveDraftServiceConflictIsConflict() throws Exception {
        recordingService.conflict = true;

        mockMvc.perform(put("/api/v1/student-attendance/sessions/99/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedSessionVersion": 0,
                                  "records": [
                                    {
                                      "studentEnrollmentId": 31,
                                      "recordedStatus": "PRESENT"
                                    }
                                  ]
                                }
                                """)
                        .with(jwtWithOrganizationUserAndRecordPermission()))
                .andExpect(status().isConflict());
    }

    @Test
    void submitAcceptsExpectedVersionBody() throws Exception {
        mockMvc.perform(post("/api/v1/student-attendance/sessions/99/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedSessionVersion": 3
                                }
                                """)
                        .with(jwtWithOrganizationUserAndSubmitPermission()))
                .andExpect(status().isOk());

        assertThat(recordingService.sessionId).isEqualTo(99L);
        assertThat(recordingService.submitRequest.expectedSessionVersion())
                .isEqualTo(3L);
    }

    @Test
    void submitValidationFailuresAreBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/student-attendance/sessions/99/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(jwtWithOrganizationUserAndSubmitPermission()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/student-attendance/sessions/99/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(jwtWithOrganizationUserAndSubmitPermission()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/student-attendance/sessions/99/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedSessionVersion": -1
                                }
                                """)
                        .with(jwtWithOrganizationUserAndSubmitPermission()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitServiceConflictIsConflict() throws Exception {
        recordingService.conflict = true;

        mockMvc.perform(post("/api/v1/student-attendance/sessions/99/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedSessionVersion": 0
                                }
                                """)
                        .with(jwtWithOrganizationUserAndSubmitPermission()))
                .andExpect(status().isConflict());
    }

    @Test
    void submitRequiresSubmitAuthority() throws Exception {
        mockMvc.perform(post("/api/v1/student-attendance/sessions/99/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedSessionVersion": 0
                                }
                                """)
                        .with(jwtWithOrganizationUserAndRecordPermission()))
                .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor jwtWithOrganizationUserAndRecordPermission() {
        return jwt()
                .jwt(builder -> builder
                        .subject("11")
                        .claim("organizationId", 7L))
                .authorities(() -> "STUDENT_ATTENDANCE_RECORD");
    }

    private static RequestPostProcessor jwtWithOrganizationUserAndSubmitPermission() {
        return jwt()
                .jwt(builder -> builder
                        .subject("11")
                        .claim("organizationId", 7L))
                .authorities(() -> "STUDENT_ATTENDANCE_SUBMIT");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> null;
        }

        @Bean
        StubRecordingService studentAttendanceRecordingService() {
            return new StubRecordingService();
        }

        @Bean
        StubAccessibleSectionService studentAttendanceAccessibleSectionService() {
            return new StubAccessibleSectionService();
        }
    }

    static class StubAccessibleSectionService
            implements StudentAttendanceAccessibleSectionService {
        long organizationId;
        long actorUserId;
        List<AccessibleStudentAttendanceSectionResponse> responses =
                List.of();

        void reset() {
            organizationId = 0L;
            actorUserId = 0L;
            responses = List.of();
        }

        @Override
        public List<AccessibleStudentAttendanceSectionResponse> listAccessibleSections(
                long organizationId,
                long actorUserId
        ) {
            this.organizationId = organizationId;
            this.actorUserId = actorUserId;
            return responses;
        }
    }

    static class StubRecordingService implements StudentAttendanceRecordingService {
        long organizationId;
        long actorUserId;
        long academicYearId;
        long gradeLevelId;
        long sectionId;
        long sessionId;
        LocalDate attendanceDate;
        BulkStudentAttendanceRecordRequest saveRequest;
        SubmitStudentAttendanceRequest submitRequest;
        boolean conflict;
        StudentAttendanceOfflineDraftSnapshot snapshot =
                new StudentAttendanceOfflineDraftSnapshot(null, List.of());

        void reset() {
            organizationId = 0L;
            actorUserId = 0L;
            academicYearId = 0L;
            gradeLevelId = 0L;
            sectionId = 0L;
            sessionId = 0L;
            attendanceDate = null;
            saveRequest = null;
            submitRequest = null;
            conflict = false;
            snapshot = new StudentAttendanceOfflineDraftSnapshot(null, List.of());
        }

        @Override
        public StudentAttendanceOfflineDraftSnapshot previewOfflineDraft(
                long organizationId,
                long actorUserId,
                long academicYearId,
                long gradeLevelId,
                long sectionId,
                LocalDate attendanceDate
        ) {
            this.organizationId = organizationId;
            this.actorUserId = actorUserId;
            this.academicYearId = academicYearId;
            this.gradeLevelId = gradeLevelId;
            this.sectionId = sectionId;
            this.attendanceDate = attendanceDate;
            return snapshot;
        }

        @Override
        public com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncAppliedResult synchronizeOfflineDraft(
                long organizationId,
                long actorUserId,
                com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncRequest request
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StudentAttendanceSessionResponse getOrCreateDraft(
                long organizationId,
                long actorUserId,
                long academicYearId,
                long gradeLevelId,
                long sectionId,
                LocalDate attendanceDate
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StudentAttendanceSessionResponse getSession(
                long organizationId,
                long actorUserId,
                long sessionId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StudentAttendanceSessionResponse saveDraftRecords(
                long organizationId,
                long actorUserId,
                long sessionId,
                BulkStudentAttendanceRecordRequest request
        ) {
            if (conflict) {
                throw new StudentAttendanceRecordingConflictException(
                        "Attendance changed. Please reload and try again."
                );
            }
            this.organizationId = organizationId;
            this.actorUserId = actorUserId;
            this.sessionId = sessionId;
            this.saveRequest = request;
            return null;
        }

        @Override
        public StudentAttendanceSessionResponse submitManually(
                long organizationId,
                long actorUserId,
                long sessionId,
                SubmitStudentAttendanceRequest request
        ) {
            if (conflict) {
                throw new StudentAttendanceRecordingConflictException(
                        "Attendance changed. Please reload and try again."
                );
            }
            this.organizationId = organizationId;
            this.actorUserId = actorUserId;
            this.sessionId = sessionId;
            this.submitRequest = request;
            return null;
        }

        @Override
        public StudentAttendanceSessionResponse getSectionAttendanceForDate(
                long organizationId,
                long actorUserId,
                long academicYearId,
                long gradeLevelId,
                long sectionId,
                LocalDate attendanceDate
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
