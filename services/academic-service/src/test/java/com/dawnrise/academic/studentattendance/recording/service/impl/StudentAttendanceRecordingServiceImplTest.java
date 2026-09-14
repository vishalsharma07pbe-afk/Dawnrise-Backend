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
import com.dawnrise.academic.studentattendance.notificationoutbox.service.StudentAttendanceNotificationOutboxService;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendancePolicyRepository;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendanceStatusPolicyRepository;
import com.dawnrise.academic.studentattendance.policy.service.StudentLatePenaltyCalculator;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineDraftSnapshot;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceSession;
import com.dawnrise.academic.studentattendance.recording.exception.InvalidStudentAttendanceRecordingException;
import com.dawnrise.academic.studentattendance.recording.exception.StudentAttendanceRecordingConflictException;
import com.dawnrise.academic.studentattendance.recording.mapper.StudentAttendanceRecordingMapper;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceRecord;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceRecordRepository;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceSessionRepository;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSessionStatus;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentenrollment.repository.StudentEnrollmentRepository;
import com.dawnrise.academic.teacherassignment.repository.TeacherAssignmentRepository;
import org.springframework.security.access.AccessDeniedException;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
    private EnrollmentRepositoryStub enrollmentRepository;
    private TeacherAssignmentRepositoryStub teacherAssignmentRepository;
    private TimeZoneClientStub timeZoneClient;
    private StudentAttendanceNotificationOutboxService
            notificationOutboxService;
    private StudentAttendanceRecordingServiceImpl service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        sessionRepository = new SessionRepositoryStub();
        recordRepository = new RecordRepositoryStub();
        contextRepository = new ContextRepositoryStub();
        enrollmentRepository = new EnrollmentRepositoryStub();
        teacherAssignmentRepository = new TeacherAssignmentRepositoryStub();
        timeZoneClient = new TimeZoneClientStub();
        notificationOutboxService =
                mock(StudentAttendanceNotificationOutboxService.class);
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
        verifyNoInteractions(notificationOutboxService);
    }

    @Test
    void manualSubmissionCreatesOutboxEventsInsideSubmissionFlow() {
        StudentAttendanceSession session = session(ATTENDANCE_DATE);
        StudentAttendanceRecord record = record(session, 200L);
        sessionRepository.findById = Optional.of(session);
        recordRepository.records = List.of(record);
        enrollmentRepository.enrollments = List.of(enrollment(200L));
        contextRepository.stubContext(AcademicYearStatus.ACTIVE, ATTENDANCE_DATE);

        service.submitManually(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                100L
        );

        verify(notificationOutboxService)
                .createForSubmittedSession(session, List.of(record));
    }

    @Test
    void manualSubmissionFailureDoesNotCreateOutboxEvents() {
        sessionRepository.findById = Optional.of(session(ATTENDANCE_DATE));
        recordRepository.records = List.of();
        contextRepository.stubContext(AcademicYearStatus.ACTIVE, ATTENDANCE_DATE);

        assertThatThrownBy(() -> service.submitManually(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                100L
        )).isInstanceOf(InvalidStudentAttendanceRecordingException.class)
                .hasMessage("Attendance session cannot be submitted without records");
        verifyNoInteractions(notificationOutboxService);
    }

    @Test
    void outboxFailurePropagatesFromManualSubmissionTransaction() {
        StudentAttendanceSession session = session(ATTENDANCE_DATE);
        StudentAttendanceRecord record = record(session, 200L);
        sessionRepository.findById = Optional.of(session);
        recordRepository.records = List.of(record);
        enrollmentRepository.enrollments = List.of(enrollment(200L));
        contextRepository.stubContext(AcademicYearStatus.ACTIVE, ATTENDANCE_DATE);
        doThrow(new IllegalStateException("outbox failed"))
                .when(notificationOutboxService)
                .createForSubmittedSession(session, List.of(record));

        assertThatThrownBy(() -> service.submitManually(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                100L
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox failed");
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

    @Test
    void previewOfflineDraftReturnsDateEligibleRosterStudentUserIdsAndNullVersionsWithoutSession() {
        contextRepository.stubContext(AcademicYearStatus.ACTIVE, ATTENDANCE_DATE);
        sessionRepository.findBySectionDate = Optional.empty();
        enrollmentRepository.enrollments = List.of(
                enrollment(200L),
                enrollment(201L)
        );

        StudentAttendanceOfflineDraftSnapshot snapshot =
                service.previewOfflineDraft(
                        ORGANIZATION_ID,
                        ACTOR_USER_ID,
                        ACADEMIC_YEAR_ID,
                        GRADE_LEVEL_ID,
                        SECTION_ID,
                        ATTENDANCE_DATE
                );

        assertThat(snapshot.baseSessionVersion()).isNull();
        assertThat(snapshot.roster())
                .extracting(
                        entry -> entry.studentEnrollmentId(),
                        entry -> entry.studentUserId(),
                        entry -> entry.rollNumber(),
                        entry -> entry.existingRecordVersion()
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(200L, 1200L, "200", null),
                        org.assertj.core.groups.Tuple.tuple(201L, 1201L, "201", null)
                );
        assertThat(enrollmentRepository.lastAttendanceDate)
                .isEqualTo(ATTENDANCE_DATE);
    }

    @Test
    void previewOfflineDraftReturnsPersistedSessionAndRecordVersions() {
        contextRepository.stubContext(AcademicYearStatus.ACTIVE, ATTENDANCE_DATE);
        StudentAttendanceSession session = session(ATTENDANCE_DATE);
        ReflectionTestUtils.setField(session, "version", 7L);
        StudentAttendanceRecord record = record(session, 200L);
        ReflectionTestUtils.setField(record, "version", 4L);
        sessionRepository.findBySectionDate = Optional.of(session);
        recordRepository.records = List.of(record);
        enrollmentRepository.enrollments = List.of(enrollment(200L), enrollment(201L));

        StudentAttendanceOfflineDraftSnapshot snapshot =
                service.previewOfflineDraft(
                        ORGANIZATION_ID,
                        ACTOR_USER_ID,
                        ACADEMIC_YEAR_ID,
                        GRADE_LEVEL_ID,
                        SECTION_ID,
                        ATTENDANCE_DATE
                );

        assertThat(snapshot.baseSessionVersion()).isEqualTo(7L);
        assertThat(snapshot.roster())
                .extracting(
                        entry -> entry.studentEnrollmentId(),
                        entry -> entry.existingRecordVersion()
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(200L, 4L),
                        org.assertj.core.groups.Tuple.tuple(201L, null)
                );
    }

    @Test
    void previewOfflineDraftRejectsNonDraftExistingSession() {
        contextRepository.stubContext(AcademicYearStatus.ACTIVE, ATTENDANCE_DATE);
        StudentAttendanceSession session = session(ATTENDANCE_DATE);
        ReflectionTestUtils.setField(
                session,
                "lifecycleStatus",
                StudentAttendanceSessionStatus.SUBMITTED
        );
        sessionRepository.findBySectionDate = Optional.of(session);

        assertThatThrownBy(() -> service.previewOfflineDraft(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                ATTENDANCE_DATE
        )).isInstanceOf(StudentAttendanceRecordingConflictException.class);
    }

    @Test
    void previewOfflineDraftRejectsUnassignedTeacher() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(
                        "teacher",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))
                )
        );
        contextRepository.stubContext(AcademicYearStatus.ACTIVE, ATTENDANCE_DATE);

        assertThatThrownBy(() -> service.previewOfflineDraft(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                ATTENDANCE_DATE
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void previewOfflineDraftAllowsAssignedTeacher() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(
                        "teacher",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))
                )
        );
        teacherAssignmentRepository.assigned = true;
        contextRepository.stubContext(AcademicYearStatus.ACTIVE, ATTENDANCE_DATE);

        assertThatCode(() -> service.previewOfflineDraft(
                ORGANIZATION_ID,
                ACTOR_USER_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                ATTENDANCE_DATE
        )).doesNotThrowAnyException();
    }

    private StudentAttendanceRecordingServiceImpl service(Clock testClock) {
        return new StudentAttendanceRecordingServiceImpl(
                sessionRepository.repository(),
                recordRepository.repository(),
                contextRepository.academicYearRepository(),
                contextRepository.sectionRepository(),
                contextRepository.calendarDayRepository(),
                enrollmentRepository.repository(),
                proxy(StudentAttendancePolicyRepository.class, unsupported()),
                proxy(StudentAttendanceStatusPolicyRepository.class, unsupported()),
                teacherAssignmentRepository.repository(),
                new StudentLatePenaltyCalculator(),
                new StudentAttendanceRecordingMapper(),
                timeZoneClient,
                notificationOutboxService,
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

    private static StudentAttendanceRecord record(
            StudentAttendanceSession session,
            long enrollmentId
    ) {
        StudentAttendanceRecord record = new StudentAttendanceRecord(
                session,
                enrollmentId,
                enrollmentId + 1000L,
                AttendanceStatus.ABSENT,
                AttendanceStatus.ABSENT,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                false,
                null,
                ACTOR_USER_ID
        );
        ReflectionTestUtils.setField(record, "id", enrollmentId + 2000L);
        return record;
    }

    private static StudentEnrollment enrollment(long id) {
        StudentEnrollment enrollment = new StudentEnrollment(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                id + 1000L,
                Long.toString(id),
                ATTENDANCE_DATE
        );
        ReflectionTestUtils.setField(enrollment, "id", id);
        return enrollment;
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
        private List<StudentAttendanceRecord> records = List.of();

        private StudentAttendanceRecordRepository repository() {
            return proxy(StudentAttendanceRecordRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findAllByAttendanceSessionIdOrderByIdAsc" -> records;
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }
    }

    private static final class EnrollmentRepositoryStub {
        private List<StudentEnrollment> enrollments = List.of();
        private LocalDate lastAttendanceDate;

        private StudentEnrollmentRepository repository() {
            return proxy(StudentEnrollmentRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findEligibleForAttendanceDate" -> {
                    lastAttendanceDate = (LocalDate) args[4];
                    yield enrollments;
                }
                default -> defaultObjectMethod(proxy, method.getName(), args);
            });
        }
    }

    private static final class TeacherAssignmentRepositoryStub {
        private boolean assigned;

        private TeacherAssignmentRepository repository() {
            return proxy(TeacherAssignmentRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndTeacherUserId" -> assigned;
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
