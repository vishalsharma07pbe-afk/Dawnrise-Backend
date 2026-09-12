package com.dawnrise.academic.studentattendance.recording.service.impl;

import com.dawnrise.academic.academiccalendar.entity.AcademicCalendarDay;
import com.dawnrise.academic.academiccalendar.enums.AttendanceRequirement;
import com.dawnrise.academic.academiccalendar.enums.CalendarDayType;
import com.dawnrise.academic.academiccalendar.repository.AcademicCalendarDayRepository;
import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.common.integration.school.SchoolTimeZoneClient;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendancePolicyRepository;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendanceStatusPolicyRepository;
import com.dawnrise.academic.studentattendance.policy.service.StudentLatePenaltyCalculator;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceSession;
import com.dawnrise.academic.studentattendance.recording.exception.InvalidStudentAttendanceRecordingException;
import com.dawnrise.academic.studentattendance.recording.exception.StudentAttendanceRecordingConflictException;
import com.dawnrise.academic.studentattendance.recording.mapper.StudentAttendanceRecordingMapper;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceRecordRepository;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceSessionRepository;
import com.dawnrise.academic.studentenrollment.repository.StudentEnrollmentRepository;
import com.dawnrise.academic.teacherassignment.repository.TeacherAssignmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAttendanceRecordingServiceImplTest {

    private static final long ORGANIZATION_ID = 10L;
    private static final long ACADEMIC_YEAR_ID = 20L;
    private static final long GRADE_LEVEL_ID = 30L;
    private static final long SECTION_ID = 40L;
    private static final long ACTOR_USER_ID = 50L;
    private static final LocalDate ATTENDANCE_DATE =
            LocalDate.of(2026, 4, 10);

    private SessionRepositoryStub sessionRepository;
    private RecordRepositoryStub recordRepository;
    private ContextRepositoryStub contextRepository;
    private TimeZoneClientStub timeZoneClient;
    private StudentAttendanceRecordingServiceImpl service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        sessionRepository = new SessionRepositoryStub();
        recordRepository = new RecordRepositoryStub();
        contextRepository = new ContextRepositoryStub();
        timeZoneClient = new TimeZoneClientStub();
        clock = Clock.fixed(
                Instant.parse("2026-04-10T08:00:00Z"),
                ZoneId.of("UTC")
        );
        service = service(clock);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(
                        "admin",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void plannedYearMutationIsRejected() {
        contextRepository.stubContext(AcademicYearStatus.PLANNED, ATTENDANCE_DATE);

        assertThatThrownBy(() -> createDraft(ATTENDANCE_DATE))
                .isInstanceOf(StudentAttendanceRecordingConflictException.class);
    }

    @Test
    void closedYearMutationIsRejected() {
        contextRepository.stubContext(AcademicYearStatus.CLOSED, ATTENDANCE_DATE);

        assertThatThrownBy(() -> createDraft(ATTENDANCE_DATE))
                .isInstanceOf(StudentAttendanceRecordingConflictException.class);
    }

    @Test
    void voidedYearMutationIsRejected() {
        contextRepository.stubContext(AcademicYearStatus.VOIDED, ATTENDANCE_DATE);

        assertThatThrownBy(() -> createDraft(ATTENDANCE_DATE))
                .isInstanceOf(StudentAttendanceRecordingConflictException.class);
    }

    @Test
    void activeYearMutationIsAcceptedForToday() {
        contextRepository.stubContext(AcademicYearStatus.ACTIVE, ATTENDANCE_DATE);
        sessionRepository.findBySectionDate = Optional.empty();

        assertThatCode(() -> createDraft(ATTENDANCE_DATE))
                .doesNotThrowAnyException();
    }

    @Test
    void futureDateIsRejectedUsingSchoolTimezone() {
        LocalDate futureDate = LocalDate.of(2026, 4, 11);
        contextRepository.stubContext(AcademicYearStatus.ACTIVE, futureDate);

        assertThatThrownBy(() -> createDraft(futureDate))
                .isInstanceOf(InvalidStudentAttendanceRecordingException.class)
                .hasMessage("Attendance date cannot be in the future");
    }

    @Test
    void pastDateIsAccepted() {
        LocalDate pastDate = LocalDate.of(2026, 4, 9);
        contextRepository.stubContext(AcademicYearStatus.ACTIVE, pastDate);
        sessionRepository.findBySectionDate = Optional.of(session(pastDate));

        assertThatCode(() -> createDraft(pastDate))
                .doesNotThrowAnyException();
    }

    @Test
    void utcBoundaryUsesSchoolLocalDateInsteadOfUtcDate() {
        Clock boundaryClock = Clock.fixed(
                Instant.parse("2026-04-10T23:30:00Z"),
                ZoneId.of("UTC")
        );
        service = service(boundaryClock);
        LocalDate schoolToday = LocalDate.of(2026, 4, 11);
        contextRepository.stubContext(AcademicYearStatus.ACTIVE, schoolToday);
        timeZoneClient.zone = ZoneId.of("Asia/Kolkata");
        sessionRepository.findBySectionDate = Optional.of(session(schoolToday));

        assertThatCode(() -> createDraft(schoolToday))
                .doesNotThrowAnyException();
    }

    @Test
    void saveDraftValidatesPersistedSessionYearAndDate() {
        sessionRepository.findById = Optional.of(session(ATTENDANCE_DATE));
        contextRepository.stubContext(AcademicYearStatus.CLOSED, ATTENDANCE_DATE);

        assertThatThrownBy(() -> service.saveDraftRecords(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                100L,
                null
        )).isInstanceOf(StudentAttendanceRecordingConflictException.class);
    }

    @Test
    void submitValidatesPersistedSessionYearAndDate() {
        sessionRepository.findById = Optional.of(session(ATTENDANCE_DATE));
        contextRepository.stubContext(AcademicYearStatus.CLOSED, ATTENDANCE_DATE);

        assertThatThrownBy(() -> service.submitManually(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                100L
        )).isInstanceOf(StudentAttendanceRecordingConflictException.class);
    }

    @Test
    void historicalGetSessionWorksForClosedYearWithoutTimezoneLookup() {
        sessionRepository.findById = Optional.of(session(ATTENDANCE_DATE));

        assertThatCode(() -> service.getSession(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                100L
        )).doesNotThrowAnyException();
        assertThat(timeZoneClient.calls).isZero();
    }

    @Test
    void historicalGetSectionAttendanceForDateWorksForClosedYearWithoutTimezoneLookup() {
        contextRepository.stubContext(AcademicYearStatus.CLOSED, ATTENDANCE_DATE);
        sessionRepository.findBySectionDate = Optional.of(session(ATTENDANCE_DATE));

        assertThatCode(() -> service.getSectionAttendanceForDate(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                ATTENDANCE_DATE
        )).doesNotThrowAnyException();
        assertThat(timeZoneClient.calls).isZero();
    }

    private StudentAttendanceRecordingServiceImpl service(Clock testClock) {
        return new StudentAttendanceRecordingServiceImpl(
                sessionRepository.repository(),
                recordRepository.repository(),
                contextRepository.academicYearRepository(),
                contextRepository.sectionRepository(),
                contextRepository.calendarDayRepository(),
                proxy(StudentEnrollmentRepository.class, unsupported()),
                proxy(StudentAttendancePolicyRepository.class, unsupported()),
                proxy(StudentAttendanceStatusPolicyRepository.class, unsupported()),
                proxy(TeacherAssignmentRepository.class, (proxy, method, args) -> {
                    if (method.getName().equals("existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndTeacherUserId")) {
                        return false;
                    }
                    return defaultObjectMethod(proxy, method.getName(), args);
                }),
                new StudentLatePenaltyCalculator(),
                new StudentAttendanceRecordingMapper(),
                timeZoneClient,
                testClock
        );
    }

    private void createDraft(LocalDate date) {
        service.getOrCreateDraft(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                date
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

    private static Section section() {
        Section section = new Section(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                "A",
                "A",
                1
        );
        ReflectionTestUtils.setField(section, "id", SECTION_ID);
        return section;
    }

    private static AcademicCalendarDay calendarDay(LocalDate attendanceDate) {
        AcademicCalendarDay day = new AcademicCalendarDay(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                attendanceDate,
                CalendarDayType.WORKING_DAY,
                null,
                AttendanceRequirement.REQUIRED,
                true,
                BigDecimal.ONE,
                null,
                ACTOR_USER_ID
        );
        ReflectionTestUtils.setField(day, "id", 70L);
        return day;
    }

    private static StudentAttendanceSession session(LocalDate attendanceDate) {
        StudentAttendanceSession session = new StudentAttendanceSession(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                70L,
                attendanceDate,
                ACTOR_USER_ID
        );
        ReflectionTestUtils.setField(session, "id", 100L);
        return session;
    }

    private static <T> T proxy(
            Class<T> type,
            InvocationHandler handler
    ) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler
        ));
    }

    private static InvocationHandler unsupported() {
        return (proxy, method, args) -> defaultObjectMethod(
                proxy,
                method.getName(),
                args
        );
    }

    private static Object defaultObjectMethod(
            Object proxy,
            String methodName,
            Object[] args
    ) {
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

    private static final class SessionRepositoryStub {
        private Optional<StudentAttendanceSession> findBySectionDate =
                Optional.empty();
        private Optional<StudentAttendanceSession> findById =
                Optional.empty();

        private StudentAttendanceSessionRepository repository() {
            return proxy(StudentAttendanceSessionRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findByOrganizationIdAndAcademicYearIdAndSectionIdAndAttendanceDate" -> findBySectionDate;
                case "findByIdAndOrganizationId" -> findById;
                case "saveAndFlush" -> {
                    StudentAttendanceSession session = (StudentAttendanceSession) args[0];
                    ReflectionTestUtils.setField(session, "id", 100L);
                    yield session;
                }
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }
    }

    private static final class RecordRepositoryStub {
        private StudentAttendanceRecordRepository repository() {
            return proxy(StudentAttendanceRecordRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findAllByAttendanceSessionIdOrderByIdAsc" -> List.of();
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }
    }

    private static final class ContextRepositoryStub {
        private AcademicYear year;
        private Section section;
        private AcademicCalendarDay calendarDay;

        private void stubContext(
                AcademicYearStatus status,
                LocalDate attendanceDate
        ) {
            year = academicYear(status);
            section = section();
            calendarDay = calendarDay(attendanceDate);
        }

        private AcademicYearRepository academicYearRepository() {
            return proxy(AcademicYearRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findByIdAndOrganizationId" -> Optional.ofNullable(year);
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }

        private SectionRepository sectionRepository() {
            return proxy(SectionRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId" -> Optional.ofNullable(section);
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }

        private AcademicCalendarDayRepository calendarDayRepository() {
            return proxy(AcademicCalendarDayRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findByOrganizationIdAndAcademicYearIdAndCalendarDate" -> Optional.ofNullable(calendarDay);
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }
    }

    private static final class TimeZoneClientStub
            implements SchoolTimeZoneClient {
        private ZoneId zone = ZoneId.of("Asia/Kolkata");
        private int calls;

        @Override
        public ZoneId getTimeZone(long organizationId) {
            calls++;
            return zone;
        }
    }
}
