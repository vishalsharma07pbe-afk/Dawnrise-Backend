package com.dawnrise.academic.studentattendance.recording.service.impl;

import com.dawnrise.academic.academiccalendar.entity.AcademicCalendarDay;
import com.dawnrise.academic.academiccalendar.enums.AttendanceRequirement;
import com.dawnrise.academic.academiccalendar.enums.CalendarDayType;
import com.dawnrise.academic.academiccalendar.repository.AcademicCalendarDayRepository;
import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.studentattendance.policy.entity.StudentAttendancePolicy;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceMode;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.policy.enums.LateCountingPeriod;
import com.dawnrise.academic.studentattendance.policy.enums.LatePenaltyOutcome;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendancePolicyRepository;
import com.dawnrise.academic.studentattendance.notificationoutbox.service.StudentAttendanceNotificationOutboxService;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceRecord;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceSession;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSessionStatus;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSubmissionType;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceRecordRepository;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceSessionRepository;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentenrollment.repository.StudentEnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class StudentAttendanceAutomaticSubmissionSessionProcessorTest {

    private static final long ORGANIZATION_ID = 11L;
    private static final long ACADEMIC_YEAR_ID = 21L;
    private static final long GRADE_LEVEL_ID = 31L;
    private static final long SECTION_ID = 41L;
    private static final long SESSION_ID = 51L;
    private static final LocalDate ATTENDANCE_DATE = LocalDate.of(2026, 9, 12);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-12T06:00:00Z");

    private final StubState state = new StubState();
    private StudentAttendanceNotificationOutboxService
            notificationOutboxService;
    private StudentAttendanceAutomaticSubmissionSessionProcessor processor;

    @BeforeEach
    void setUp() {
        notificationOutboxService =
                mock(StudentAttendanceNotificationOutboxService.class);
        processor = new StudentAttendanceAutomaticSubmissionSessionProcessor(
                state.sessionRepository(),
                state.recordRepository(),
                state.academicYearRepository(),
                state.calendarDayRepository(),
                state.enrollmentRepository(),
                state.policyRepository(),
                notificationOutboxService
        );
    }

    @Test
    void cutoffIsBasedOnUpdatedAtInactivity() {
        StudentAttendanceSession session = completeSession(NOW.minusMinutes(20));
        state.stubCompleteContext(session, true, AcademicYearStatus.ACTIVE, List.of(1L, 2L), List.of(1L, 2L));

        assertThat(processor.processCandidate(SESSION_ID, ATTENDANCE_DATE, NOW)).isTrue();

        assertThat(session.getLifecycleStatus()).isEqualTo(StudentAttendanceSessionStatus.SUBMITTED);
        assertThat(session.getSubmissionType()).isEqualTo(StudentAttendanceSubmissionType.AUTOMATIC);
        assertThat(session.getSubmittedByUserId()).isNull();
        assertThat(session.getSubmittedAt()).isEqualTo(NOW);
        verify(notificationOutboxService)
                .createForSubmittedSession(session, state.records);
    }

    @Test
    void doesNotUseMidnightBasedCutoff() {
        StudentAttendanceSession session = completeSession(NOW.minusMinutes(5));
        state.stubCompleteContext(session, true, AcademicYearStatus.ACTIVE, List.of(1L), List.of(1L));

        assertThat(processor.processCandidate(SESSION_ID, ATTENDANCE_DATE, NOW)).isFalse();
        assertThat(session.getLifecycleStatus()).isEqualTo(StudentAttendanceSessionStatus.DRAFT);
        verifyNoInteractions(notificationOutboxService);
    }

    @Test
    void notYetDueIsSkipped() {
        StudentAttendanceSession session = completeSession(NOW.minusMinutes(9).minusSeconds(59));
        state.stubCompleteContext(session, true, AcademicYearStatus.ACTIVE, List.of(1L), List.of(1L));

        assertThat(processor.processCandidate(SESSION_ID, ATTENDANCE_DATE, NOW)).isFalse();
    }

    @Test
    void policyDisabledIsSkipped() {
        StudentAttendanceSession session = completeSession(NOW.minusMinutes(20));
        state.stubCompleteContext(session, false, AcademicYearStatus.ACTIVE, List.of(1L), List.of(1L));

        assertThat(processor.processCandidate(SESSION_ID, ATTENDANCE_DATE, NOW)).isFalse();
    }

    @Test
    void nonActiveYearIsSkipped() {
        StudentAttendanceSession session = completeSession(NOW.minusMinutes(20));
        state.stubCompleteContext(session, true, AcademicYearStatus.CLOSED, List.of(1L), List.of(1L));

        assertThat(processor.processCandidate(SESSION_ID, ATTENDANCE_DATE, NOW)).isFalse();
    }

    @Test
    void emptyRosterIsSkipped() {
        StudentAttendanceSession session = completeSession(NOW.minusMinutes(20));
        state.stubCompleteContext(session, true, AcademicYearStatus.ACTIVE, List.of(), List.of(1L));

        assertThat(processor.processCandidate(SESSION_ID, ATTENDANCE_DATE, NOW)).isFalse();
    }

    @Test
    void emptyRecordsAreSkipped() {
        StudentAttendanceSession session = completeSession(NOW.minusMinutes(20));
        state.stubCompleteContext(session, true, AcademicYearStatus.ACTIVE, List.of(1L), List.of());

        assertThat(processor.processCandidate(SESSION_ID, ATTENDANCE_DATE, NOW)).isFalse();
    }

    @Test
    void missingRecordIsSkipped() {
        StudentAttendanceSession session = completeSession(NOW.minusMinutes(20));
        state.stubCompleteContext(session, true, AcademicYearStatus.ACTIVE, List.of(1L, 2L), List.of(1L));

        assertThat(processor.processCandidate(SESSION_ID, ATTENDANCE_DATE, NOW)).isFalse();
    }

    @Test
    void extraStaleRecordIsSkipped() {
        StudentAttendanceSession session = completeSession(NOW.minusMinutes(20));
        state.stubCompleteContext(session, true, AcademicYearStatus.ACTIVE, List.of(1L), List.of(1L, 999L));

        assertThat(processor.processCandidate(SESSION_ID, ATTENDANCE_DATE, NOW)).isFalse();
    }

    @Test
    void manualSubmissionRaceIsSkipped() {
        StudentAttendanceSession session = completeSession(NOW.minusMinutes(20));
        session.submitManually(99L);
        state.session = session;

        assertThat(processor.processCandidate(SESSION_ID, ATTENDANCE_DATE, NOW)).isFalse();
        assertThat(state.policyLookupCount).isZero();
    }

    @Test
    void recordUpdateRaceIsSkippedWhenUpdatedAtIsNoLongerDue() {
        StudentAttendanceSession session = completeSession(NOW.minusMinutes(1));
        state.stubCompleteContext(session, true, AcademicYearStatus.ACTIVE, List.of(1L), List.of(1L));

        assertThat(processor.processCandidate(SESSION_ID, ATTENDANCE_DATE, NOW)).isFalse();
    }

    @Test
    void twoWorkersDoNotDoubleSubmit() {
        StudentAttendanceSession session = completeSession(NOW.minusMinutes(20));
        state.stubCompleteContext(session, true, AcademicYearStatus.ACTIVE, List.of(1L), List.of(1L));

        assertThat(processor.processCandidate(SESSION_ID, ATTENDANCE_DATE, NOW)).isTrue();
        assertThat(processor.processCandidate(SESSION_ID, ATTENDANCE_DATE, NOW)).isFalse();
    }

    @Test
    void outboxFailurePropagatesFromAutomaticSubmissionTransaction() {
        StudentAttendanceSession session = completeSession(NOW.minusMinutes(20));
        state.stubCompleteContext(session, true, AcademicYearStatus.ACTIVE, List.of(1L), List.of(1L));
        doThrow(new IllegalStateException("outbox failed"))
                .when(notificationOutboxService)
                .createForSubmittedSession(session, state.records);

        assertThatThrownBy(() ->
                processor.processCandidate(SESSION_ID, ATTENDANCE_DATE, NOW)
        ).isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox failed");
    }

    private static StudentAttendanceSession completeSession(OffsetDateTime updatedAt) {
        StudentAttendanceSession session = new StudentAttendanceSession(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                61L,
                ATTENDANCE_DATE,
                71L
        );
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        ReflectionTestUtils.setField(session, "updatedAt", updatedAt);
        return session;
    }

    private static StudentAttendancePolicy policy(boolean automaticSubmissionEnabled) {
        return new StudentAttendancePolicy(
                ORGANIZATION_ID,
                AttendanceMode.DAILY,
                java.time.DayOfWeek.MONDAY,
                5,
                10,
                false,
                3,
                LatePenaltyOutcome.HALF_DAY,
                LateCountingPeriod.MONTHLY,
                true,
                0,
                30,
                automaticSubmissionEnabled,
                71L
        );
    }

    private static AcademicYear academicYear(AcademicYearStatus status) {
        AcademicYear year = new AcademicYear(
                ORGANIZATION_ID,
                "2026",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2027, 3, 31)
        );
        ReflectionTestUtils.setField(year, "id", ACADEMIC_YEAR_ID);
        ReflectionTestUtils.setField(year, "status", status);
        return year;
    }

    private static AcademicCalendarDay calendarDay() {
        AcademicCalendarDay day = new AcademicCalendarDay(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                ATTENDANCE_DATE,
                CalendarDayType.WORKING_DAY,
                null,
                AttendanceRequirement.REQUIRED,
                true,
                BigDecimal.ONE,
                null,
                71L
        );
        ReflectionTestUtils.setField(day, "id", 61L);
        return day;
    }

    private static StudentEnrollment enrollment(long id) {
        StudentEnrollment enrollment = new StudentEnrollment(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                1000L + id,
                Long.toString(id),
                ATTENDANCE_DATE
        );
        ReflectionTestUtils.setField(enrollment, "id", id);
        return enrollment;
    }

    private static StudentAttendanceRecord record(StudentAttendanceSession session, long enrollmentId) {
        return new StudentAttendanceRecord(
                session,
                enrollmentId,
                1000L + enrollmentId,
                AttendanceStatus.PRESENT,
                AttendanceStatus.PRESENT,
                BigDecimal.ONE,
                BigDecimal.ONE,
                false,
                null,
                71L
        );
    }

    private static final class StubState {
        private StudentAttendanceSession session;
        private StudentAttendancePolicy policy;
        private AcademicYear year;
        private AcademicCalendarDay calendarDay;
        private List<StudentEnrollment> enrollments = List.of();
        private List<StudentAttendanceRecord> records = List.of();
        private int policyLookupCount;

        private void stubCompleteContext(
                StudentAttendanceSession session,
                boolean automaticSubmissionEnabled,
                AcademicYearStatus yearStatus,
                List<Long> eligibleEnrollmentIds,
                List<Long> recordEnrollmentIds
        ) {
            this.session = session;
            this.policy = policy(automaticSubmissionEnabled);
            this.year = academicYear(yearStatus);
            this.calendarDay = calendarDay();
            this.enrollments = eligibleEnrollmentIds.stream()
                    .map(StudentAttendanceAutomaticSubmissionSessionProcessorTest::enrollment)
                    .toList();
            this.records = recordEnrollmentIds.stream()
                    .map(id -> record(session, id))
                    .toList();
        }

        private StudentAttendanceSessionRepository sessionRepository() {
            return proxy(StudentAttendanceSessionRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findByIdForAutomaticSubmissionUpdate" -> Optional.ofNullable(session);
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }

        private StudentAttendancePolicyRepository policyRepository() {
            return proxy(StudentAttendancePolicyRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findById" -> {
                    policyLookupCount++;
                    yield Optional.ofNullable(policy);
                }
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }

        private AcademicYearRepository academicYearRepository() {
            return proxy(AcademicYearRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findByIdAndOrganizationId" -> Optional.ofNullable(year);
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }

        private AcademicCalendarDayRepository calendarDayRepository() {
            return proxy(AcademicCalendarDayRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findByOrganizationIdAndAcademicYearIdAndCalendarDate" -> Optional.ofNullable(calendarDay);
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }

        private StudentEnrollmentRepository enrollmentRepository() {
            return proxy(StudentEnrollmentRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findEligibleForAttendanceDate" -> enrollments;
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }

        private StudentAttendanceRecordRepository recordRepository() {
            return proxy(StudentAttendanceRecordRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findAllByAttendanceSessionIdOrderByIdAsc" -> records;
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler
        ));
    }

    private static Object defaultObjectMethod(Object proxy, String methodName, Object[] args) {
        if (methodName.equals("hashCode")) {
            return System.identityHashCode(proxy);
        }
        if (methodName.equals("equals")) {
            return proxy == args[0];
        }
        if (methodName.equals("toString")) {
            return "RepositoryStub";
        }
        throw new UnsupportedOperationException(methodName);
    }
}
