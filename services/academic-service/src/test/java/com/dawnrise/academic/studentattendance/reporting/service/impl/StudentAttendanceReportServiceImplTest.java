package com.dawnrise.academic.studentattendance.reporting.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.common.integration.school.SchoolTimeZoneClient;
import com.dawnrise.academic.common.integration.school.SchoolTimeZoneUnavailableException;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceSession;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceSessionRepository;
import com.dawnrise.academic.studentattendance.reporting.dto.AttendanceReportDateRange;
import com.dawnrise.academic.studentattendance.reporting.exception.InvalidStudentAttendanceReportException;
import com.dawnrise.academic.studentattendance.reporting.exception.StudentAttendanceReportNotFoundException;
import com.dawnrise.academic.studentattendance.reporting.mapper.StudentAttendanceReportMapper;
import com.dawnrise.academic.studentattendance.reporting.projection.*;
import com.dawnrise.academic.studentattendance.reporting.repository.StudentAttendanceReportRepository;
import com.dawnrise.academic.studentattendance.reporting.security.StudentAttendanceReportAccessService;
import com.dawnrise.academic.studentattendance.reporting.validation.StudentAttendanceReportValidator;
import com.dawnrise.academic.studentenrollment.repository.StudentEnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.*;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StudentAttendanceReportServiceImplTest {

    private StudentAttendanceReportRepository reportRepository;
    private StudentAttendanceSessionRepository sessionRepository;
    private StudentEnrollmentRepository enrollmentRepository;
    private StudentAttendanceReportValidator validator;
    private StudentAttendanceReportAccessService accessService;
    private SchoolTimeZoneClient schoolTimeZoneClient;
    private StudentAttendanceReportServiceImpl service;
    private AcademicYear academicYear;

    @BeforeEach
    void setUp() {
        reportRepository = mock(StudentAttendanceReportRepository.class);
        sessionRepository = mock(StudentAttendanceSessionRepository.class);
        enrollmentRepository = mock(StudentEnrollmentRepository.class);
        validator = mock(StudentAttendanceReportValidator.class);
        accessService = mock(StudentAttendanceReportAccessService.class);
        schoolTimeZoneClient = mock(SchoolTimeZoneClient.class);
        service = new StudentAttendanceReportServiceImpl(
                reportRepository,
                sessionRepository,
                enrollmentRepository,
                new StudentAttendanceReportMapper(),
                validator,
                accessService,
                schoolTimeZoneClient,
                Clock.fixed(
                        Instant.parse("2026-09-14T18:30:00Z"),
                        ZoneId.of("UTC")
                )
        );
        academicYear = new AcademicYear(
                7L,
                "2026",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2027, 3, 31)
        );
    }

    @Test
    void dailyReportUsesOnlySubmittedSessionAndMapsRowsAndSummary()
            throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 11);
        StudentAttendanceSession session = submittedSession(date);
        when(validator.requireAcademicYear(7L, 8L))
                .thenReturn(academicYear);
        when(sessionRepository
                .findByOrganizationIdAndAcademicYearIdAndSectionIdAndAttendanceDate(
                        7L,
                        8L,
                        10L,
                        date
                )).thenReturn(Optional.of(session));
        when(reportRepository.findDailySectionRecords(7L, 8L, 9L, 10L, date))
                .thenReturn(List.of(projection(
                        DailyStudentAttendanceProjection.class,
                        Map.of(
                                "attendanceRecordId", 31L,
                                "studentEnrollmentId", 32L,
                                "studentUserId", 33L,
                                "rollNumber", "A-1",
                                "recordedStatus", "LATE",
                                "effectiveStatus", "HALF_DAY",
                                "earnedCredit", new BigDecimal("0.50"),
                                "possibleCredit", BigDecimal.ONE
                        )
                )));
        when(reportRepository.summarizeSection(
                7L,
                8L,
                9L,
                10L,
                date,
                date
        )).thenReturn(projection(
                AttendanceAggregateProjection.class,
                Map.of(
                        "halfDayCount", 1L,
                        "earnedCredit", new BigDecimal("0.50"),
                        "possibleCredit", BigDecimal.ONE,
                        "submittedSessionCount", 1L,
                        "distinctStudentCount", 1L
                )
        ));

        var report = service.getDailySectionReport(
                7L,
                11L,
                8L,
                9L,
                10L,
                date
        );

        assertThat(report.attendanceSessionId()).isEqualTo(21L);
        assertThat(report.students()).hasSize(1);
        assertThat(report.students().get(0).effectiveStatus().name())
                .isEqualTo("HALF_DAY");
        assertThat(report.summary().credits().attendancePercentage())
                .isEqualByComparingTo("50.00");
        verify(accessService).requireSectionAccess(7L, 8L, 9L, 10L, 11L);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void draftOrMissingDailySessionReturnsNotFoundBeforeReportQueries()
            throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 11);
        when(validator.requireAcademicYear(7L, 8L))
                .thenReturn(academicYear);
        when(sessionRepository
                .findByOrganizationIdAndAcademicYearIdAndSectionIdAndAttendanceDate(
                        7L,
                        8L,
                        10L,
                        date
                )).thenReturn(Optional.of(draftSession(date)));

        assertThatThrownBy(() -> service.getDailySectionReport(
                7L,
                11L,
                8L,
                9L,
                10L,
                date
        )).isInstanceOf(StudentAttendanceReportNotFoundException.class);

        verifyNoInteractions(reportRepository);
    }

    @Test
    void studentHistoryIsScopedAndAcceptsHistoricalEnrollment() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        PageRequest requested =
                PageRequest.of(2, 20, Sort.by("ignored"));
        PageRequest safe = PageRequest.of(2, 20);
        when(validator.requireAcademicYear(7L, 8L))
                .thenReturn(academicYear);
        when(validator.requireDateRange(academicYear, from, to))
                .thenReturn(new AttendanceReportDateRange(from, to));
        when(validator.sanitizePageable(requested)).thenReturn(safe);
        when(enrollmentRepository
                .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndStudentUserId(
                        7L,
                        8L,
                        9L,
                        10L,
                        99L
                )).thenReturn(true);
        when(reportRepository.findStudentHistory(
                7L,
                8L,
                99L,
                9L,
                10L,
                from,
                to,
                safe
        )).thenReturn(new PageImpl<>(List.of(), safe, 0));

        var report = service.getStudentHistory(
                7L,
                11L,
                8L,
                9L,
                10L,
                99L,
                from,
                to,
                requested
        );

        assertThat(report.records().pageNumber()).isEqualTo(2);
        assertThat(report.records().pageSize()).isEqualTo(20);
        assertThat(report.records().content()).isEmpty();
        verify(reportRepository).summarizeStudent(
                7L,
                8L,
                99L,
                9L,
                10L,
                from,
                to
        );
    }

    @Test
    void missingStudentEnrollmentIsRejected() {
        when(validator.requireAcademicYear(7L, 8L))
                .thenReturn(academicYear);

        assertThatThrownBy(() -> service.getStudentMonthlySummary(
                7L,
                11L,
                8L,
                9L,
                10L,
                99L,
                YearMonth.of(2026, 9)
        )).isInstanceOf(StudentAttendanceReportNotFoundException.class);

        verifyNoInteractions(reportRepository);
    }

    @Test
    void monthlyRangeAndSubmittedDayMappingUseStudentScopedArgumentOrder() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        when(validator.requireAcademicYear(7L, 8L))
                .thenReturn(academicYear);
        when(enrollmentRepository
                .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndStudentUserId(
                        7L,
                        8L,
                        9L,
                        10L,
                        99L
                )).thenReturn(true);
        when(validator.requireMonthRange(
                academicYear,
                YearMonth.of(2026, 9)
        )).thenReturn(new AttendanceReportDateRange(from, to));
        when(reportRepository.summarizeStudentMonth(
                7L,
                8L,
                99L,
                9L,
                10L,
                from,
                to
        )).thenReturn(projection(
                StudentMonthlyAttendanceProjection.class,
                Map.of(
                        "presentCount", 1L,
                        "submittedAttendanceDays", 2L,
                        "earnedCredit", BigDecimal.ONE,
                        "possibleCredit", BigDecimal.ONE
                )
        ));

        var report = service.getStudentMonthlySummary(
                7L,
                11L,
                8L,
                9L,
                10L,
                99L,
                YearMonth.of(2026, 9)
        );

        assertThat(report.submittedAttendanceDays()).isEqualTo(2);
        assertThat(report.summary().statusCounts().presentCount())
                .isEqualTo(1);
    }

    @Test
    void sectionAndGradeSummariesReturnZeroForEmptyAggregates() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        when(validator.requireAcademicYear(7L, 8L))
                .thenReturn(academicYear);
        when(validator.requireDateRange(academicYear, from, to))
                .thenReturn(new AttendanceReportDateRange(from, to));
        when(reportRepository.summarizeGradeSections(
                7L,
                8L,
                9L,
                from,
                to
        )).thenReturn(List.of());

        var section = service.getSectionSummary(
                7L,
                11L,
                8L,
                9L,
                10L,
                from,
                to
        );
        var grade = service.getGradeSummary(
                7L,
                11L,
                8L,
                9L,
                from,
                to
        );

        assertThat(section.summary().statusCounts().totalCount()).isZero();
        assertThat(section.submittedSessionCount()).isZero();
        assertThat(grade.summary().credits().possibleCredit())
                .isEqualByComparingTo("0.00");
        verify(accessService).requireLeadershipAccess();
    }

    @Test
    void lowAttendanceAndMissingScopesUseSectionOrLeadershipRules() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 15);
        PageRequest requested = PageRequest.of(0, 25);
        PageRequest safe = PageRequest.of(0, 25);
        when(validator.requireAcademicYear(7L, 8L))
                .thenReturn(academicYear);
        when(validator.requireDateRange(academicYear, from, to))
                .thenReturn(new AttendanceReportDateRange(from, to));
        when(validator.requireThresholdPercentage(new BigDecimal("75.00")))
                .thenReturn(new BigDecimal("75.00"));
        when(validator.sanitizePageable(requested)).thenReturn(safe);
        when(schoolTimeZoneClient.getTimeZone(7L))
                .thenReturn(ZoneId.of("Asia/Kolkata"));
        when(reportRepository.findLowAttendanceStudents(
                7L,
                8L,
                9L,
                10L,
                from,
                to,
                new BigDecimal("75.00"),
                safe
        )).thenReturn(Page.empty(safe));
        when(reportRepository.findMissingOrIncompleteSessions(
                7L,
                8L,
                null,
                null,
                from,
                to,
                safe
        )).thenReturn(new SliceImpl<>(List.of(), safe, false));

        service.getLowAttendanceStudents(
                7L,
                11L,
                8L,
                9L,
                10L,
                from,
                to,
                new BigDecimal("75.00"),
                requested
        );
        service.getMissingAttendance(
                7L,
                11L,
                8L,
                null,
                null,
                from,
                to,
                requested
        );

        verify(accessService).requireSectionAccess(7L, 8L, 9L, 10L, 11L);
        verify(accessService).requireLeadershipAccess();
        verify(validator).requireOptionalScope(7L, 8L, 9L, 10L);
        verify(validator).requireOptionalScope(7L, 8L, null, null);
    }

    @Test
    void missingAttendanceAllowsSchoolLocalTodayAndQueriesRepository() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate today = LocalDate.of(2026, 9, 15);
        PageRequest requested = PageRequest.of(0, 25);
        PageRequest safe = PageRequest.of(0, 25);
        when(validator.requireAcademicYear(7L, 8L))
                .thenReturn(academicYear);
        when(validator.requireDateRange(academicYear, from, today))
                .thenReturn(new AttendanceReportDateRange(from, today));
        when(schoolTimeZoneClient.getTimeZone(7L))
                .thenReturn(ZoneId.of("Asia/Kolkata"));
        when(validator.sanitizePageable(requested)).thenReturn(safe);
        when(reportRepository.findMissingOrIncompleteSessions(
                7L,
                8L,
                9L,
                10L,
                from,
                today,
                safe
        )).thenReturn(new SliceImpl<>(List.of(), safe, false));

        var report = service.getMissingAttendance(
                7L,
                11L,
                8L,
                9L,
                10L,
                from,
                today,
                requested
        );

        assertThat(report.dateRange().toDate()).isEqualTo(today);
        verify(reportRepository).findMissingOrIncompleteSessions(
                7L,
                8L,
                9L,
                10L,
                from,
                today,
                safe
        );
    }

    @Test
    void missingAttendanceRejectsFutureToDateBeforeRepositoryExecution() {
        LocalDate future = LocalDate.of(2026, 9, 16);
        LocalDate from = future;
        PageRequest requested = PageRequest.of(0, 25);
        when(validator.requireAcademicYear(7L, 8L))
                .thenReturn(academicYear);
        when(validator.requireDateRange(academicYear, from, future))
                .thenReturn(new AttendanceReportDateRange(from, future));
        when(schoolTimeZoneClient.getTimeZone(7L))
                .thenReturn(ZoneId.of("Asia/Kolkata"));

        assertThatThrownBy(() -> service.getMissingAttendance(
                7L,
                11L,
                8L,
                9L,
                10L,
                from,
                future,
                requested
        )).isInstanceOf(InvalidStudentAttendanceReportException.class)
                .hasMessage(
                        "Missing attendance report cannot include future dates"
                );

        verify(validator, never()).sanitizePageable(any());
        verify(reportRepository, never())
                .findMissingOrIncompleteSessions(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    void missingAttendanceTimezoneLookupFailureStopsBeforeRepositoryExecution() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 15);
        PageRequest requested = PageRequest.of(0, 25);
        SchoolTimeZoneUnavailableException failure =
                new SchoolTimeZoneUnavailableException(
                        "School timezone could not be verified"
                );
        when(validator.requireAcademicYear(7L, 8L))
                .thenReturn(academicYear);
        when(validator.requireDateRange(academicYear, from, to))
                .thenReturn(new AttendanceReportDateRange(from, to));
        when(schoolTimeZoneClient.getTimeZone(7L))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.getMissingAttendance(
                7L,
                11L,
                8L,
                9L,
                10L,
                from,
                to,
                requested
        )).isSameAs(failure);

        verify(validator, never()).sanitizePageable(any());
        verify(reportRepository, never())
                .findMissingOrIncompleteSessions(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    void lowAttendanceAndMissingStopBeforeRepositoryWhenScopeDenied() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        when(validator.requireAcademicYear(7L, 8L))
                .thenReturn(academicYear);
        doThrow(new AccessDeniedException("Access Denied"))
                .when(accessService)
                .requireLeadershipAccess();

        assertThatThrownBy(() -> service.getLowAttendanceStudents(
                7L,
                11L,
                8L,
                null,
                null,
                from,
                to,
                new BigDecimal("75.00"),
                PageRequest.of(0, 20)
        )).isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> service.getMissingAttendance(
                7L,
                11L,
                8L,
                null,
                null,
                from,
                to,
                PageRequest.of(0, 20)
        )).isInstanceOf(AccessDeniedException.class);

        verify(reportRepository, never()).findLowAttendanceStudents(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
        verify(reportRepository, never())
                .findMissingOrIncompleteSessions(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                );
    }

    @SuppressWarnings("unchecked")
    private static <T> T projection(
            Class<T> projectionType,
            Map<String, Object> values
    ) {
        return (T) Proxy.newProxyInstance(
                projectionType.getClassLoader(),
                new Class<?>[]{projectionType},
                (proxy, method, args) -> {
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    if (method.getName().equals("toString")) {
                        return projectionType.getSimpleName() + values;
                    }
                    if (method.getName().startsWith("get")) {
                        String property =
                                Character.toLowerCase(
                                        method.getName().charAt(3)
                                ) + method.getName().substring(4);
                        return values.get(property);
                    }
                    throw new UnsupportedOperationException(
                            method.getName()
                    );
                }
        );
    }

    private static StudentAttendanceSession submittedSession(LocalDate date)
            throws Exception {
        StudentAttendanceSession session =
                new StudentAttendanceSession(
                        7L,
                        8L,
                        9L,
                        10L,
                        20L,
                        date,
                        11L
                );
        session.submitManually(11L);
        setField(session, "id", 21L);
        return session;
    }

    private static StudentAttendanceSession draftSession(LocalDate date)
            throws Exception {
        StudentAttendanceSession session =
                new StudentAttendanceSession(
                        7L,
                        8L,
                        9L,
                        10L,
                        20L,
                        date,
                        11L
                );
        setField(session, "id", 21L);
        return session;
    }

    private static void setField(
            Object target,
            String fieldName,
            Object value
    ) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
