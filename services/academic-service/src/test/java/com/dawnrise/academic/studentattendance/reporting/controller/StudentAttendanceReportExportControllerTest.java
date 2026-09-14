package com.dawnrise.academic.studentattendance.reporting.controller;

import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.config.SecurityConfig;
import com.dawnrise.academic.security.AcademicTenantSecurity;
import com.dawnrise.academic.studentattendance.reporting.export.StudentAttendanceReportExport;
import com.dawnrise.academic.studentattendance.reporting.export.StudentAttendanceReportExportFormat;
import com.dawnrise.academic.studentattendance.reporting.export.StudentAttendanceReportExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentAttendanceReportExportController.class)
@Import({
        SecurityConfig.class,
        AcademicTenantSecurity.class,
        GlobalExceptionHandler.class,
        StudentAttendanceReportExportControllerTest.TestConfig.class
})
class StudentAttendanceReportExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubExportService service;

    @BeforeEach
    void resetService() {
        service.reset();
    }

    @Test
    void allExportEndpointsRequireStudentAttendanceViewAuthority()
            throws Exception {
        for (String url : urls()) {
            mockMvc.perform(get(url).with(jwtWithoutPermission()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void exportEndpointsForwardJwtActorAndReturnSafeDownloadHeaders()
            throws Exception {
        assertExportResponse(dailyUrl());
        assertExportResponse(historyUrl());
        assertExportResponse(sectionSummaryUrl());
        assertExportResponse(lowAttendanceUrl());

        assertThat(service.calls).containsExactly(
                "daily",
                "history",
                "summary",
                "low"
        );
        assertThat(service.organizationId).isEqualTo(7L);
        assertThat(service.actorUserId).isEqualTo(11L);
        assertThat(service.academicYearId).isEqualTo(8L);
        assertThat(service.gradeLevelId).isEqualTo(9L);
        assertThat(service.sectionId).isEqualTo(10L);
        assertThat(service.studentUserId).isEqualTo(99L);
        assertThat(service.thresholdPercentage)
                .isEqualByComparingTo("75.00");
        assertThat(service.format)
                .isEqualTo(StudentAttendanceReportExportFormat.CSV);
    }

    @Test
    void invalidFormatAndInvalidIdsReturn400() throws Exception {
        mockMvc.perform(get(dailyUrl().replace("format=CSV", "format=PDF"))
                        .with(jwtWithPermission()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(
                        "/api/v1/student-attendance/reports/"
                                + "academic-years/0/grades/9/sections/10"
                                + "/daily/export"
                                + "?attendanceDate=2026-09-11&format=CSV"
                ).with(jwtWithPermission()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fileGenerationFailuresReturnSafe500() throws Exception {
        service.fail = true;

        mockMvc.perform(get(dailyUrl()).with(jwtWithPermission()))
                .andExpect(status().isInternalServerError())
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.jsonPath("$.message")
                        .value("An unexpected error occurred."))
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.jsonPath("$.message")
                        .value(org.hamcrest.Matchers.not(
                                containsString("POI")
                        )));
    }

    private void assertExportResponse(String url) throws Exception {
        mockMvc.perform(get(url).with(jwtWithPermission()))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Type",
                        containsString("text/csv")
                ))
                .andExpect(header().string("Content-Length", "6"))
                .andExpect(header().string(
                        "Content-Disposition",
                        containsString(
                                "attachment; filename=\"student-attendance.csv\""
                        )
                ))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    private static List<String> urls() {
        return List.of(
                dailyUrl(),
                historyUrl(),
                sectionSummaryUrl(),
                lowAttendanceUrl()
        );
    }

    private static String dailyUrl() {
        return "/api/v1/student-attendance/reports/"
                + "academic-years/8/grades/9/sections/10"
                + "/daily/export?attendanceDate=2026-09-11&format=CSV";
    }

    private static String historyUrl() {
        return "/api/v1/student-attendance/reports/"
                + "academic-years/8/grades/9/sections/10"
                + "/students/99/history/export"
                + "?fromDate=2026-09-01&toDate=2026-09-30&format=CSV";
    }

    private static String sectionSummaryUrl() {
        return "/api/v1/student-attendance/reports/"
                + "academic-years/8/grades/9/sections/10"
                + "/summary/export"
                + "?fromDate=2026-09-01&toDate=2026-09-30&format=CSV";
    }

    private static String lowAttendanceUrl() {
        return "/api/v1/student-attendance/reports/"
                + "academic-years/8/low-attendance/export"
                + "?gradeLevelId=9&sectionId=10"
                + "&fromDate=2026-09-01&toDate=2026-09-30"
                + "&thresholdPercentage=75.00&format=CSV";
    }

    private static RequestPostProcessor jwtWithPermission() {
        return jwt()
                .jwt(builder -> builder
                        .subject("11")
                        .claim("organizationId", 7L))
                .authorities(new SimpleGrantedAuthority(
                        "STUDENT_ATTENDANCE_VIEW"
                ));
    }

    private static RequestPostProcessor jwtWithoutPermission() {
        return jwt()
                .jwt(builder -> builder
                        .subject("11")
                        .claim("organizationId", 7L));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> null;
        }

        @Bean
        StubExportService studentAttendanceReportExportService() {
            return new StubExportService();
        }
    }

    static class StubExportService
            implements StudentAttendanceReportExportService {

        long organizationId;
        long actorUserId;
        long academicYearId;
        Long gradeLevelId;
        Long sectionId;
        long studentUserId;
        BigDecimal thresholdPercentage;
        StudentAttendanceReportExportFormat format;
        boolean fail;
        final java.util.ArrayList<String> calls =
                new java.util.ArrayList<>();

        @Override
        public StudentAttendanceReportExport exportDailySection(
                long organizationId,
                long actorUserId,
                long academicYearId,
                long gradeLevelId,
                long sectionId,
                LocalDate attendanceDate,
                StudentAttendanceReportExportFormat format
        ) {
            capture(
                    "daily",
                    organizationId,
                    actorUserId,
                    academicYearId,
                    gradeLevelId,
                    sectionId,
                    format
            );
            return export();
        }

        @Override
        public StudentAttendanceReportExport exportStudentHistory(
                long organizationId,
                long actorUserId,
                long academicYearId,
                long gradeLevelId,
                long sectionId,
                long studentUserId,
                LocalDate fromDate,
                LocalDate toDate,
                StudentAttendanceReportExportFormat format
        ) {
            capture(
                    "history",
                    organizationId,
                    actorUserId,
                    academicYearId,
                    gradeLevelId,
                    sectionId,
                    format
            );
            this.studentUserId = studentUserId;
            return export();
        }

        @Override
        public StudentAttendanceReportExport exportSectionSummary(
                long organizationId,
                long actorUserId,
                long academicYearId,
                long gradeLevelId,
                long sectionId,
                LocalDate fromDate,
                LocalDate toDate,
                StudentAttendanceReportExportFormat format
        ) {
            capture(
                    "summary",
                    organizationId,
                    actorUserId,
                    academicYearId,
                    gradeLevelId,
                    sectionId,
                    format
            );
            return export();
        }

        @Override
        public StudentAttendanceReportExport exportLowAttendance(
                long organizationId,
                long actorUserId,
                long academicYearId,
                Long gradeLevelId,
                Long sectionId,
                LocalDate fromDate,
                LocalDate toDate,
                BigDecimal thresholdPercentage,
                StudentAttendanceReportExportFormat format
        ) {
            capture(
                    "low",
                    organizationId,
                    actorUserId,
                    academicYearId,
                    gradeLevelId,
                    sectionId,
                    format
            );
            this.thresholdPercentage = thresholdPercentage;
            return export();
        }

        private void capture(
                String call,
                long organizationId,
                long actorUserId,
                long academicYearId,
                Long gradeLevelId,
                Long sectionId,
                StudentAttendanceReportExportFormat format
        ) {
            calls.add(call);
            this.organizationId = organizationId;
            this.actorUserId = actorUserId;
            this.academicYearId = academicYearId;
            this.gradeLevelId = gradeLevelId;
            this.sectionId = sectionId;
            this.format = format;
        }

        private StudentAttendanceReportExport export() {
            if (fail) {
                throw new IllegalStateException(
                        "POI could not write /tmp/private.xlsx"
                );
            }
            return new StudentAttendanceReportExport(
                    "student-attendance.csv",
                    StudentAttendanceReportExportFormat.CSV.contentType(),
                    "export".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        }

        private void reset() {
            organizationId = 0L;
            actorUserId = 0L;
            academicYearId = 0L;
            gradeLevelId = null;
            sectionId = null;
            studentUserId = 0L;
            thresholdPercentage = null;
            format = null;
            fail = false;
            calls.clear();
        }
    }
}
