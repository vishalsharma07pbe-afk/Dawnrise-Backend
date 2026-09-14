package com.dawnrise.academic.studentattendance.reporting.controller;

import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.config.SecurityConfig;
import com.dawnrise.academic.security.AcademicTenantSecurity;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSessionStatus;
import com.dawnrise.academic.studentattendance.reporting.dto.*;
import com.dawnrise.academic.studentattendance.reporting.service.StudentAttendanceReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentAttendanceReportController.class)
@Import({
        SecurityConfig.class,
        AcademicTenantSecurity.class,
        GlobalExceptionHandler.class,
        StudentAttendanceReportControllerTest.TestConfig.class
})
class StudentAttendanceReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubService service;

    @BeforeEach
    void resetService() {
        service.reset();
    }

    @Test
    void allEndpointsRequireStudentAttendanceViewAuthority()
            throws Exception {
        mockMvc.perform(get(dailyUrl())
                        .with(jwtWithoutPermission()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(historyUrl())
                        .with(jwtWithoutPermission()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(monthlyUrl())
                        .with(jwtWithoutPermission()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(sectionSummaryUrl())
                        .with(jwtWithoutPermission()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(gradeSummaryUrl())
                        .with(jwtWithoutPermission()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(lowAttendanceUrl())
                        .with(jwtWithoutPermission()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(missingUrl())
                        .with(jwtWithoutPermission()))
                .andExpect(status().isForbidden());
    }

    @Test
    void allEndpointsPassJwtActorAndRouteParameters()
            throws Exception {
        service.daily = new DailySectionAttendanceReportResponse(
                1L,
                7L,
                8L,
                9L,
                10L,
                20L,
                LocalDate.of(2026, 9, 11),
                StudentAttendanceSessionStatus.SUBMITTED,
                null,
                null,
                AttendanceSummary.empty(),
                List.of()
        );
        service.history = new StudentAttendanceHistoryReportResponse(
                7L,
                8L,
                9L,
                10L,
                99L,
                range(),
                AttendanceSummary.empty(),
                AttendancePageResponse.from(
                        org.springframework.data.domain.Page.empty()
                )
        );
        service.monthly = new MonthlyStudentAttendanceReportResponse(
                7L,
                8L,
                9L,
                10L,
                99L,
                YearMonth.of(2026, 9),
                2L,
                AttendanceSummary.empty()
        );
        service.section = new SectionAttendanceSummaryResponse(
                7L,
                8L,
                9L,
                10L,
                range(),
                1L,
                2L,
                AttendanceSummary.empty()
        );
        service.grade = new GradeAttendanceSummaryResponse(
                7L,
                8L,
                9L,
                range(),
                AttendanceSummary.empty(),
                List.of()
        );
        service.low = new LowAttendanceReportResponse(
                7L,
                8L,
                9L,
                10L,
                range(),
                new BigDecimal("75.00"),
                AttendancePageResponse.from(
                        org.springframework.data.domain.Page.empty()
                )
        );
        service.missing = new MissingAttendanceReportResponse(
                7L,
                8L,
                9L,
                10L,
                range(),
                0,
                20,
                false,
                List.of(new MissingAttendanceSessionResponse(
                        21L,
                        LocalDate.of(2026, 9, 11),
                        9L,
                        10L,
                        "A",
                        "Alpha",
                        null,
                        null,
                        30L,
                        0L,
                        false,
                        true
                ))
        );

        mockMvc.perform(get(dailyUrl()).with(jwtWithPermission()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceSessionId").value(1));
        mockMvc.perform(get(historyUrl()).with(jwtWithPermission()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentUserId").value(99));
        mockMvc.perform(get(monthlyUrl()).with(jwtWithPermission()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submittedAttendanceDays").value(2));
        mockMvc.perform(get(sectionSummaryUrl()).with(jwtWithPermission()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value(10));
        mockMvc.perform(get(gradeSummaryUrl()).with(jwtWithPermission()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gradeLevelId").value(9));
        mockMvc.perform(get(lowAttendanceUrl()).with(jwtWithPermission()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thresholdPercentage").value(75.00));
        mockMvc.perform(get(missingUrl()).with(jwtWithPermission()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.items[0].sessionMissing")
                        .value(true))
                .andExpect(jsonPath("$.items[0].rosterIncomplete")
                        .value(false));

        assertThat(service.organizationId).isEqualTo(7L);
        assertThat(service.actorUserId).isEqualTo(11L);
        assertThat(service.academicYearId).isEqualTo(8L);
        assertThat(service.gradeLevelId).isEqualTo(9L);
        assertThat(service.sectionId).isEqualTo(10L);
        assertThat(service.studentUserId).isEqualTo(99L);
        assertThat(service.thresholdPercentage)
                .isEqualByComparingTo("75.00");
    }

    @Test
    void invalidPositiveConstraintsReturn400() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/student-attendance/reports/"
                                + "academic-years/0/grades/9/sections/10/daily"
                )
                        .param("attendanceDate", "2026-09-11")
                        .with(jwtWithPermission()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serviceScopeDenialReturns403WithoutInternalDetails()
            throws Exception {
        service.deny = true;

        mockMvc.perform(get(gradeSummaryUrl()).with(jwtWithPermission()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access Denied"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("SQL")
                        )));
    }

    private static String dailyUrl() {
        return "/api/v1/student-attendance/reports/"
                + "academic-years/8/grades/9/sections/10/daily"
                + "?attendanceDate=2026-09-11";
    }

    private static String historyUrl() {
        return "/api/v1/student-attendance/reports/"
                + "academic-years/8/grades/9/sections/10/"
                + "students/99/history"
                + "?fromDate=2026-09-01&toDate=2026-09-30"
                + "&page=2&size=25&sort=ignored";
    }

    private static String monthlyUrl() {
        return "/api/v1/student-attendance/reports/"
                + "academic-years/8/grades/9/sections/10/"
                + "students/99/monthly?month=2026-09";
    }

    private static String sectionSummaryUrl() {
        return "/api/v1/student-attendance/reports/"
                + "academic-years/8/grades/9/sections/10/summary"
                + "?fromDate=2026-09-01&toDate=2026-09-30";
    }

    private static String gradeSummaryUrl() {
        return "/api/v1/student-attendance/reports/"
                + "academic-years/8/grades/9/summary"
                + "?fromDate=2026-09-01&toDate=2026-09-30";
    }

    private static String lowAttendanceUrl() {
        return "/api/v1/student-attendance/reports/"
                + "academic-years/8/low-attendance"
                + "?gradeLevelId=9&sectionId=10"
                + "&fromDate=2026-09-01&toDate=2026-09-30"
                + "&thresholdPercentage=75.00&page=0&size=20";
    }

    private static String missingUrl() {
        return "/api/v1/student-attendance/reports/"
                + "academic-years/8/missing"
                + "?gradeLevelId=9&sectionId=10"
                + "&fromDate=2026-09-01&toDate=2026-09-30"
                + "&page=0&size=20";
    }

    private static AttendanceReportDateRange range() {
        return new AttendanceReportDateRange(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30)
        );
    }

    private static RequestPostProcessor jwtWithPermission() {
        return jwt()
                .jwt(builder -> builder
                        .subject("11")
                        .claim("organizationId", 7L))
                .authorities(() -> "STUDENT_ATTENDANCE_VIEW");
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
        StubService studentAttendanceReportService() {
            return new StubService();
        }
    }

    static class StubService implements StudentAttendanceReportService {
        long organizationId;
        long actorUserId;
        long academicYearId;
        Long gradeLevelId;
        Long sectionId;
        long studentUserId;
        BigDecimal thresholdPercentage;
        DailySectionAttendanceReportResponse daily;
        StudentAttendanceHistoryReportResponse history;
        MonthlyStudentAttendanceReportResponse monthly;
        SectionAttendanceSummaryResponse section;
        GradeAttendanceSummaryResponse grade;
        LowAttendanceReportResponse low;
        MissingAttendanceReportResponse missing;
        boolean deny;

        @Override
        public DailySectionAttendanceReportResponse getDailySectionReport(
                long organizationId,
                long actorUserId,
                long academicYearId,
                long gradeLevelId,
                long sectionId,
                LocalDate attendanceDate
        ) {
            capture(organizationId, actorUserId, academicYearId,
                    gradeLevelId, sectionId);
            return daily;
        }

        @Override
        public StudentAttendanceHistoryReportResponse getStudentHistory(
                long organizationId,
                long actorUserId,
                long academicYearId,
                long gradeLevelId,
                long sectionId,
                long studentUserId,
                LocalDate fromDate,
                LocalDate toDate,
                Pageable pageable
        ) {
            capture(organizationId, actorUserId, academicYearId,
                    gradeLevelId, sectionId);
            this.studentUserId = studentUserId;
            return history;
        }

        @Override
        public MonthlyStudentAttendanceReportResponse
        getStudentMonthlySummary(
                long organizationId,
                long actorUserId,
                long academicYearId,
                long gradeLevelId,
                long sectionId,
                long studentUserId,
                YearMonth month
        ) {
            capture(organizationId, actorUserId, academicYearId,
                    gradeLevelId, sectionId);
            this.studentUserId = studentUserId;
            return monthly;
        }

        @Override
        public SectionAttendanceSummaryResponse getSectionSummary(
                long organizationId,
                long actorUserId,
                long academicYearId,
                long gradeLevelId,
                long sectionId,
                LocalDate fromDate,
                LocalDate toDate
        ) {
            capture(organizationId, actorUserId, academicYearId,
                    gradeLevelId, sectionId);
            return section;
        }

        @Override
        public GradeAttendanceSummaryResponse getGradeSummary(
                long organizationId,
                long actorUserId,
                long academicYearId,
                long gradeLevelId,
                LocalDate fromDate,
                LocalDate toDate
        ) {
            capture(organizationId, actorUserId, academicYearId,
                    gradeLevelId, null);
            return grade;
        }

        @Override
        public LowAttendanceReportResponse getLowAttendanceStudents(
                long organizationId,
                long actorUserId,
                long academicYearId,
                Long gradeLevelId,
                Long sectionId,
                LocalDate fromDate,
                LocalDate toDate,
                BigDecimal thresholdPercentage,
                Pageable pageable
        ) {
            capture(organizationId, actorUserId, academicYearId,
                    gradeLevelId, sectionId);
            this.thresholdPercentage = thresholdPercentage;
            return low;
        }

        @Override
        public MissingAttendanceReportResponse getMissingAttendance(
                long organizationId,
                long actorUserId,
                long academicYearId,
                Long gradeLevelId,
                Long sectionId,
                LocalDate fromDate,
                LocalDate toDate,
                Pageable pageable
        ) {
            capture(organizationId, actorUserId, academicYearId,
                    gradeLevelId, sectionId);
            return missing;
        }

        private void capture(
                long organizationId,
                long actorUserId,
                long academicYearId,
                Long gradeLevelId,
                Long sectionId
        ) {
            if (deny) {
                throw new AccessDeniedException(
                        "select * from teacher_assignments"
                );
            }
            this.organizationId = organizationId;
            this.actorUserId = actorUserId;
            this.academicYearId = academicYearId;
            this.gradeLevelId = gradeLevelId;
            this.sectionId = sectionId;
        }

        private void reset() {
            organizationId = 0L;
            actorUserId = 0L;
            academicYearId = 0L;
            gradeLevelId = null;
            sectionId = null;
            studentUserId = 0L;
            thresholdPercentage = null;
            daily = null;
            history = null;
            monthly = null;
            section = null;
            grade = null;
            low = null;
            missing = null;
            deny = false;
        }
    }
}
