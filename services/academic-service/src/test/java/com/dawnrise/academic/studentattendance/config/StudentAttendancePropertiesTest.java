package com.dawnrise.academic.studentattendance.config;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceMode;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.policy.enums.LateCountingPeriod;
import com.dawnrise.academic.studentattendance.policy.enums.LatePenaltyOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.DayOfWeek;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendancePropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(PropertiesConfig.class);

    @Test
    void bindsValidProperties() {
        contextRunner
                .withPropertyValues(validValues())
                .run(context -> {
                    StudentAttendanceProperties properties =
                            context.getBean(StudentAttendanceProperties.class);

                    assertThat(properties.getMaxRecordsPerRequest())
                            .isEqualTo(200);
                    assertThat(properties.getMaxCalendarDaysPerAcademicYear())
                            .isEqualTo(400);
                    assertThat(properties.getDefaultDraftWarningMinutes())
                            .isEqualTo(10);
                    assertThat(properties.getDefaultAutomaticSubmissionMinutes())
                            .isEqualTo(20);
                    assertThat(properties.getDefaultAttendanceMode())
                            .isEqualTo(AttendanceMode.DAILY);
                    assertThat(properties.getDefaultWeekStartDay())
                            .isEqualTo(DayOfWeek.MONDAY);
                    assertThat(properties.getDefaultLatePenaltyEnabled())
                            .isFalse();
                    assertThat(properties.getDefaultLateOccurrencesThreshold())
                            .isEqualTo(3);
                    assertThat(properties.getDefaultLatePenaltyOutcome())
                            .isEqualTo(LatePenaltyOutcome.HALF_DAY);
                    assertThat(properties.getDefaultLateCountingPeriod())
                            .isEqualTo(LateCountingPeriod.MONTHLY);
                    assertThat(properties.getDefaultStatusCredits())
                            .containsOnlyKeys(AttendanceStatus.finalStatuses());
                    assertThat(properties.getDefaultStatusCredits()
                            .get(AttendanceStatus.LATE)
                            .getEarnedCredit())
                            .isEqualByComparingTo(new BigDecimal("1.00"));
                    assertThat(properties.getDefaultStatusCredits()
                            .get(AttendanceStatus.LATE)
                            .getPossibleCredit())
                            .isEqualByComparingTo(new BigDecimal("1.00"));
                    assertThat(properties.getDefaultWorkingDayWeight())
                            .isEqualByComparingTo(new BigDecimal("1.00"));
                });
    }

    @Test
    void missingRequiredPropertyPreventsContextStartup() {
        contextRunner
                .withPropertyValues(
                        "dawnrise.student-attendance.max-records-per-request=200",
                        "dawnrise.student-attendance.max-calendar-days-per-academic-year=400",
                        "dawnrise.student-attendance.default-draft-warning-minutes=10",
                        "dawnrise.student-attendance.default-automatic-submission-minutes=20",
                        "dawnrise.student-attendance.default-attendance-mode=DAILY",
                        "dawnrise.student-attendance.default-week-start-day=MONDAY",
                        "dawnrise.student-attendance.default-late-penalty-enabled=false",
                        "dawnrise.student-attendance.default-late-occurrences-threshold=3",
                        "dawnrise.student-attendance.default-late-penalty-outcome=HALF_DAY",
                        "dawnrise.student-attendance.default-late-counting-period=MONTHLY",
                        "dawnrise.student-attendance.default-status-credits.PRESENT.earned-credit=1.00",
                        "dawnrise.student-attendance.default-status-credits.PRESENT.possible-credit=1.00",
                        "dawnrise.student-attendance.default-status-credits.ABSENT.earned-credit=0.00",
                        "dawnrise.student-attendance.default-status-credits.ABSENT.possible-credit=1.00",
                        "dawnrise.student-attendance.default-status-credits.LATE.earned-credit=1.00",
                        "dawnrise.student-attendance.default-status-credits.LATE.possible-credit=1.00",
                        "dawnrise.student-attendance.default-status-credits.HALF_DAY.earned-credit=0.50",
                        "dawnrise.student-attendance.default-status-credits.HALF_DAY.possible-credit=1.00",
                        "dawnrise.student-attendance.default-status-credits.EXCUSED.earned-credit=0.00",
                        "dawnrise.student-attendance.default-status-credits.EXCUSED.possible-credit=0.00"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining(
                                "defaultWorkingDayWeight"
                        ));
    }

    @Test
    void invalidCrossFieldRelationshipPreventsContextStartup() {
        contextRunner
                .withPropertyValues(validValues())
                .withPropertyValues(
                        "dawnrise.student-attendance.default-automatic-submission-minutes=10"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining(
                                "Automatic submission minutes must be greater than warning minutes"
                        ));
    }

    @Test
    void outOfRangeValuesPreventContextStartup() {
        contextRunner
                .withPropertyValues(validValues())
                .withPropertyValues(
                        "dawnrise.student-attendance.max-calendar-days-per-academic-year=801"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining(
                                "maxCalendarDaysPerAcademicYear"
                        ));
    }

    @Test
    void thresholdZeroPreventsContextStartup() {
        contextRunner
                .withPropertyValues(validValues())
                .withPropertyValues(
                        "dawnrise.student-attendance.default-late-occurrences-threshold=0"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining(
                                "defaultLateOccurrencesThreshold"
                        ));
    }

    @Test
    void thresholdAboveOneHundredPreventsContextStartup() {
        contextRunner
                .withPropertyValues(validValues())
                .withPropertyValues(
                        "dawnrise.student-attendance.default-late-occurrences-threshold=101"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining(
                                "defaultLateOccurrencesThreshold"
                        ));
    }

    @Test
    void bindsValidLatePenaltyEnums() {
        contextRunner
                .withPropertyValues(validValues())
                .withPropertyValues(
                        "dawnrise.student-attendance.default-late-penalty-outcome=ABSENT",
                        "dawnrise.student-attendance.default-late-counting-period=MONTHLY"
                )
                .run(context -> {
                    StudentAttendanceProperties properties =
                            context.getBean(StudentAttendanceProperties.class);

                    assertThat(properties.getDefaultLatePenaltyOutcome())
                            .isEqualTo(LatePenaltyOutcome.ABSENT);
                    assertThat(properties.getDefaultLateCountingPeriod())
                            .isEqualTo(LateCountingPeriod.MONTHLY);
                });
    }

    private static String[] validValues() {
        return new String[]{
                "dawnrise.student-attendance.max-records-per-request=200",
                "dawnrise.student-attendance.max-calendar-days-per-academic-year=400",
                "dawnrise.student-attendance.default-draft-warning-minutes=10",
                "dawnrise.student-attendance.default-automatic-submission-minutes=20",
                "dawnrise.student-attendance.default-attendance-mode=DAILY",
                "dawnrise.student-attendance.default-week-start-day=MONDAY",
                "dawnrise.student-attendance.default-late-penalty-enabled=false",
                "dawnrise.student-attendance.default-late-occurrences-threshold=3",
                "dawnrise.student-attendance.default-late-penalty-outcome=HALF_DAY",
                "dawnrise.student-attendance.default-late-counting-period=MONTHLY",
                "dawnrise.student-attendance.default-status-credits.PRESENT.earned-credit=1.00",
                "dawnrise.student-attendance.default-status-credits.PRESENT.possible-credit=1.00",
                "dawnrise.student-attendance.default-status-credits.ABSENT.earned-credit=0.00",
                "dawnrise.student-attendance.default-status-credits.ABSENT.possible-credit=1.00",
                "dawnrise.student-attendance.default-status-credits.LATE.earned-credit=1.00",
                "dawnrise.student-attendance.default-status-credits.LATE.possible-credit=1.00",
                "dawnrise.student-attendance.default-status-credits.HALF_DAY.earned-credit=0.50",
                "dawnrise.student-attendance.default-status-credits.HALF_DAY.possible-credit=1.00",
                "dawnrise.student-attendance.default-status-credits.EXCUSED.earned-credit=0.00",
                "dawnrise.student-attendance.default-status-credits.EXCUSED.possible-credit=0.00",
                "dawnrise.student-attendance.default-working-day-weight=1.00"
        };
    }

    @Configuration
    @EnableConfigurationProperties(StudentAttendanceProperties.class)
    static class PropertiesConfig {
    }
}
