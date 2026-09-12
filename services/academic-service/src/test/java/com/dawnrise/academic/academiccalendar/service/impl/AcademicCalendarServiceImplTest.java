package com.dawnrise.academic.academiccalendar.service.impl;

import com.dawnrise.academic.academiccalendar.dto.AcademicCalendarDayResponse;
import com.dawnrise.academic.academiccalendar.dto.InitializeAcademicCalendarRequest;
import com.dawnrise.academic.academiccalendar.dto.UpdateAcademicCalendarDayRequest;
import com.dawnrise.academic.academiccalendar.entity.AcademicCalendarDay;
import com.dawnrise.academic.academiccalendar.enums.AttendanceRequirement;
import com.dawnrise.academic.academiccalendar.enums.CalendarDayType;
import com.dawnrise.academic.academiccalendar.exception.AcademicCalendarConflictException;
import com.dawnrise.academic.academiccalendar.exception.InvalidAcademicCalendarException;
import com.dawnrise.academic.academiccalendar.repository.AcademicCalendarDayRepository;
import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.studentattendance.config.StudentAttendanceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.lang.reflect.Proxy;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcademicCalendarServiceImplTest {

    private AcademicYearRepositoryStub academicYearRepositoryStub;
    private AcademicCalendarDayRepositoryStub calendarDayRepositoryStub;

    private AcademicCalendarServiceImpl service;

    @BeforeEach
    void setUp() {
        StudentAttendanceProperties properties = new StudentAttendanceProperties();
        properties.setMaxRecordsPerRequest(3);
        properties.setMaxCalendarDaysPerAcademicYear(400);
        properties.setDefaultDraftWarningMinutes(10);
        properties.setDefaultAutomaticSubmissionMinutes(20);
        properties.setDefaultWorkingDayWeight(new BigDecimal("1.00"));

        academicYearRepositoryStub = new AcademicYearRepositoryStub();
        calendarDayRepositoryStub = new AcademicCalendarDayRepositoryStub();
        service = new AcademicCalendarServiceImpl(
                academicYearRepositoryStub.repository(),
                calendarDayRepositoryStub.repository(),
                properties
        );
    }

    @Test
    void initializationUsesTenantScopedPessimisticLock() {
        academicYearRepositoryStub.foundForUpdate = Optional.of(academicYear());

        List<AcademicCalendarDayResponse> responses = service.initialize(
                10L,
                20L,
                30L,
                initializeRequest()
        );

        assertThat(responses).hasSize(3);
        assertThat(academicYearRepositoryStub.findForUpdateCalls).isEqualTo(1);
        assertThat(academicYearRepositoryStub.findCalls).isZero();
    }

    @Test
    void alreadyInitializedCalendarIsRejectedBeforeSaving() {
        academicYearRepositoryStub.foundForUpdate = Optional.of(academicYear());
        calendarDayRepositoryStub.exists = true;

        assertThatThrownBy(() -> service.initialize(
                10L,
                20L,
                30L,
                initializeRequest()
        )).isInstanceOf(AcademicCalendarConflictException.class)
                .hasMessage("Academic calendar is already initialized");

        assertThat(calendarDayRepositoryStub.saveAllAndFlushCalls).isZero();
    }

    @Test
    void uniqueConstraintRaceIsTranslatedToDomainConflict() {
        DataIntegrityViolationException dataConflict =
                new DataIntegrityViolationException("unique calendar date");
        academicYearRepositoryStub.foundForUpdate = Optional.of(academicYear());
        calendarDayRepositoryStub.saveAllAndFlushException = dataConflict;

        assertThatThrownBy(() -> service.initialize(
                10L,
                20L,
                30L,
                initializeRequest()
        )).isInstanceOf(AcademicCalendarConflictException.class)
                .hasMessage(
                        "Academic calendar has already been initialized for this academic year"
                )
                .hasCause(dataConflict);
    }

    @Test
    void rangeExactlyAtConfiguredMaximumSucceeds() {
        academicYearRepositoryStub.found = Optional.of(academicYearWithEndDate(
                LocalDate.of(2026, 4, 10)
        ));

        List<AcademicCalendarDayResponse> responses = service.getDays(
                10L,
                20L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3)
        );

        assertThat(responses).isEmpty();
        assertThat(calendarDayRepositoryStub.lastRangeStart)
                .isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(calendarDayRepositoryStub.lastRangeEnd)
                .isEqualTo(LocalDate.of(2026, 4, 3));
    }

    @Test
    void rangeAboveConfiguredMaximumFailsBeforeQueryingDays() {
        academicYearRepositoryStub.found = Optional.of(academicYearWithEndDate(
                LocalDate.of(2026, 4, 10)
        ));

        assertThatThrownBy(() -> service.getDays(
                10L,
                20L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 4)
        )).isInstanceOf(InvalidAcademicCalendarException.class)
                .hasMessage("Calendar date range exceeds configured request limit");

        assertThat(calendarDayRepositoryStub.findRangeCalls).isZero();
    }

    @Test
    void reversedRangeRemainsRejected() {
        academicYearRepositoryStub.found = Optional.of(academicYear());

        assertThatThrownBy(() -> service.getDays(
                10L,
                20L,
                LocalDate.of(2026, 4, 3),
                LocalDate.of(2026, 4, 1)
        )).isInstanceOf(InvalidAcademicCalendarException.class)
                .hasMessage("End date cannot be before start date");
    }

    @Test
    void tenantScopedAcademicYearLookupRemainsEnforced() {
        academicYearRepositoryStub.foundForUpdate = Optional.of(academicYear());

        service.initialize(10L, 20L, 30L, initializeRequest());

        assertThat(academicYearRepositoryStub.lastFindForUpdateId)
                .isEqualTo(20L);
        assertThat(academicYearRepositoryStub.lastFindForUpdateOrganizationId)
                .isEqualTo(10L);
    }

    @Test
    void updateAcceptsEveryValidExamDayCombination() {
        assertExamDayUpdateAccepted(
                AttendanceRequirement.REQUIRED,
                true,
                "1.00"
        );
        assertExamDayUpdateAccepted(
                AttendanceRequirement.REQUIRED,
                false,
                "0.00"
        );
        assertExamDayUpdateAccepted(
                AttendanceRequirement.OPTIONAL,
                false,
                "0.00"
        );
        assertExamDayUpdateAccepted(
                AttendanceRequirement.NOT_APPLICABLE,
                false,
                "0.00"
        );
    }

    @Test
    void updateRejectsInvalidExamDayAttendanceCombinations() {
        assertExamDayUpdateRejected(
                AttendanceRequirement.OPTIONAL,
                true,
                "0.00"
        );
        assertExamDayUpdateRejected(
                AttendanceRequirement.OPTIONAL,
                false,
                "0.50"
        );
        assertExamDayUpdateRejected(
                AttendanceRequirement.NOT_APPLICABLE,
                true,
                "0.00"
        );
        assertExamDayUpdateRejected(
                AttendanceRequirement.REQUIRED,
                true,
                "0.00"
        );
        assertExamDayUpdateRejected(
                AttendanceRequirement.REQUIRED,
                false,
                "0.50"
        );
    }

    private static InitializeAcademicCalendarRequest initializeRequest() {
        return new InitializeAcademicCalendarRequest(
                EnumSet.of(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY
                ),
                null
        );
    }

    private static AcademicYear academicYear() {
        return academicYearWithEndDate(LocalDate.of(2026, 4, 3));
    }

    private static AcademicYear academicYearWithEndDate(LocalDate endDate) {
        return new AcademicYear(
                10L,
                "2026-2027",
                LocalDate.of(2026, 4, 1),
                endDate
        );
    }

    private void assertExamDayUpdateAccepted(
            AttendanceRequirement requirement,
            boolean counts,
            String weight
    ) {
        academicYearRepositoryStub.found = Optional.of(academicYear());
        calendarDayRepositoryStub.found = Optional.of(calendarDay());

        AcademicCalendarDayResponse response = service.updateDay(
                10L,
                20L,
                40L,
                30L,
                examDayRequest(requirement, counts, weight)
        );

        assertThat(response.attendanceRequirement()).isEqualTo(requirement);
        assertThat(response.countsTowardPercentage()).isEqualTo(counts);
        assertThat(response.dayWeight()).isEqualByComparingTo(weight);
    }

    private void assertExamDayUpdateRejected(
            AttendanceRequirement requirement,
            boolean counts,
            String weight
    ) {
        academicYearRepositoryStub.found = Optional.of(academicYear());
        calendarDayRepositoryStub.found = Optional.of(calendarDay());

        assertThatThrownBy(() -> service.updateDay(
                10L,
                20L,
                40L,
                30L,
                examDayRequest(requirement, counts, weight)
        )).isInstanceOf(InvalidAcademicCalendarException.class);
    }

    private static UpdateAcademicCalendarDayRequest examDayRequest(
            AttendanceRequirement requirement,
            boolean counts,
            String weight
    ) {
        return new UpdateAcademicCalendarDayRequest(
                CalendarDayType.EXAM_DAY,
                null,
                requirement,
                counts,
                new BigDecimal(weight),
                null,
                null
        );
    }

    private static AcademicCalendarDay calendarDay() {
        return new AcademicCalendarDay(
                10L,
                20L,
                LocalDate.of(2026, 4, 1),
                CalendarDayType.WORKING_DAY,
                null,
                AttendanceRequirement.REQUIRED,
                true,
                BigDecimal.ONE,
                null,
                30L
        );
    }

    private static class AcademicYearRepositoryStub {

        private Optional<AcademicYear> found = Optional.empty();
        private Optional<AcademicYear> foundForUpdate = Optional.empty();
        private long lastFindForUpdateId;
        private long lastFindForUpdateOrganizationId;
        private int findCalls;
        private int findForUpdateCalls;

        private AcademicYearRepository repository() {
            return (AcademicYearRepository) Proxy.newProxyInstance(
                    AcademicYearRepository.class.getClassLoader(),
                    new Class<?>[]{AcademicYearRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByIdAndOrganizationId" -> {
                            findCalls++;
                            yield found;
                        }
                        case "findByIdAndOrganizationIdForUpdate" -> {
                            findForUpdateCalls++;
                            lastFindForUpdateId = (Long) args[0];
                            lastFindForUpdateOrganizationId = (Long) args[1];
                            yield foundForUpdate;
                        }
                        case "toString" -> "AcademicYearRepositoryStub";
                        default -> throw new UnsupportedOperationException(
                                method.getName()
                        );
                    }
            );
        }
    }

    private static class AcademicCalendarDayRepositoryStub {

        private boolean exists;
        private Optional<AcademicCalendarDay> found = Optional.empty();
        private RuntimeException saveAllAndFlushException;
        private LocalDate lastRangeStart;
        private LocalDate lastRangeEnd;
        private int saveAllAndFlushCalls;
        private int findRangeCalls;

        private AcademicCalendarDayRepository repository() {
            return (AcademicCalendarDayRepository) Proxy.newProxyInstance(
                    AcademicCalendarDayRepository.class.getClassLoader(),
                    new Class<?>[]{AcademicCalendarDayRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "existsByOrganizationIdAndAcademicYearId" -> exists;
                        case "findByIdAndOrganizationIdAndAcademicYearId" -> found;
                        case "saveAllAndFlush" -> {
                            saveAllAndFlushCalls++;
                            if (saveAllAndFlushException != null) {
                                throw saveAllAndFlushException;
                            }
                            yield List.copyOf((List<AcademicCalendarDay>) args[0]);
                        }
                        case "saveAndFlush" -> args[0];
                        case "findAllByOrganizationIdAndAcademicYearIdAndCalendarDateBetweenOrderByCalendarDateAscIdAsc" -> {
                            findRangeCalls++;
                            lastRangeStart = (LocalDate) args[2];
                            lastRangeEnd = (LocalDate) args[3];
                            yield List.of();
                        }
                        case "toString" -> "AcademicCalendarDayRepositoryStub";
                        default -> throw new UnsupportedOperationException(
                                method.getName()
                        );
                    }
            );
        }
    }
}
