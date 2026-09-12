package com.dawnrise.academic.studentattendance.config;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceMode;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "dawnrise.student-attendance")
public class StudentAttendanceProperties {

    @Min(1)
    @Max(1000)
    @NotNull
    private Integer maxRecordsPerRequest;

    @Min(1)
    @Max(800)
    @NotNull
    private Integer maxCalendarDaysPerAcademicYear;

    @Min(1)
    @Max(240)
    @NotNull
    private Integer defaultDraftWarningMinutes;

    @Min(2)
    @Max(480)
    @NotNull
    private Integer defaultAutomaticSubmissionMinutes;

    @NotNull
    private AttendanceMode defaultAttendanceMode;

    @NotNull
    private DayOfWeek defaultWeekStartDay;

    @NotNull
    private Map<AttendanceStatus, @Valid StatusCredit> defaultStatusCredits =
            new EnumMap<>(AttendanceStatus.class);

    @DecimalMin("0.01")
    @DecimalMax("1.00")
    @NotNull
    private BigDecimal defaultWorkingDayWeight;

    public Integer getMaxRecordsPerRequest() {
        return maxRecordsPerRequest;
    }

    public void setMaxRecordsPerRequest(Integer maxRecordsPerRequest) {
        this.maxRecordsPerRequest = maxRecordsPerRequest;
    }

    public Integer getMaxCalendarDaysPerAcademicYear() {
        return maxCalendarDaysPerAcademicYear;
    }

    public void setMaxCalendarDaysPerAcademicYear(
            Integer maxCalendarDaysPerAcademicYear
    ) {
        this.maxCalendarDaysPerAcademicYear =
                maxCalendarDaysPerAcademicYear;
    }

    public Integer getDefaultDraftWarningMinutes() {
        return defaultDraftWarningMinutes;
    }

    public void setDefaultDraftWarningMinutes(
            Integer defaultDraftWarningMinutes
    ) {
        this.defaultDraftWarningMinutes = defaultDraftWarningMinutes;
    }

    public Integer getDefaultAutomaticSubmissionMinutes() {
        return defaultAutomaticSubmissionMinutes;
    }

    public void setDefaultAutomaticSubmissionMinutes(
            Integer defaultAutomaticSubmissionMinutes
    ) {
        this.defaultAutomaticSubmissionMinutes =
                defaultAutomaticSubmissionMinutes;
    }

    public AttendanceMode getDefaultAttendanceMode() {
        return defaultAttendanceMode;
    }

    public void setDefaultAttendanceMode(
            AttendanceMode defaultAttendanceMode
    ) {
        this.defaultAttendanceMode = defaultAttendanceMode;
    }

    public DayOfWeek getDefaultWeekStartDay() {
        return defaultWeekStartDay;
    }

    public void setDefaultWeekStartDay(DayOfWeek defaultWeekStartDay) {
        this.defaultWeekStartDay = defaultWeekStartDay;
    }

    public Map<AttendanceStatus, StatusCredit> getDefaultStatusCredits() {
        return defaultStatusCredits;
    }

    public void setDefaultStatusCredits(
            Map<AttendanceStatus, @Valid StatusCredit> defaultStatusCredits
    ) {
        this.defaultStatusCredits = defaultStatusCredits;
    }

    public BigDecimal getDefaultWorkingDayWeight() {
        return defaultWorkingDayWeight;
    }

    public void setDefaultWorkingDayWeight(BigDecimal defaultWorkingDayWeight) {
        this.defaultWorkingDayWeight = defaultWorkingDayWeight;
    }

    @AssertTrue(message = "Automatic submission minutes must be greater than warning minutes")
    public boolean isAutomaticSubmissionAfterWarning() {
        return defaultDraftWarningMinutes == null
                || defaultAutomaticSubmissionMinutes == null
                || defaultAutomaticSubmissionMinutes
                > defaultDraftWarningMinutes;
    }

    @AssertTrue(message = "Default attendance status credits must include exactly final attendance statuses")
    public boolean isDefaultStatusCreditsComplete() {
        return defaultStatusCredits != null
                && defaultStatusCredits.keySet()
                .equals(AttendanceStatus.finalStatuses());
    }

    public static class StatusCredit {

        @DecimalMin("0.00")
        @DecimalMax("1.00")
        @NotNull
        private BigDecimal earnedCredit;

        @DecimalMin("0.00")
        @DecimalMax("1.00")
        @NotNull
        private BigDecimal possibleCredit;

        public BigDecimal getEarnedCredit() {
            return earnedCredit;
        }

        public void setEarnedCredit(BigDecimal earnedCredit) {
            this.earnedCredit = earnedCredit;
        }

        public BigDecimal getPossibleCredit() {
            return possibleCredit;
        }

        public void setPossibleCredit(BigDecimal possibleCredit) {
            this.possibleCredit = possibleCredit;
        }

        @AssertTrue(message = "Earned credit must not exceed possible credit")
        public boolean isEarnedCreditNotGreaterThanPossibleCredit() {
            return earnedCredit == null
                    || possibleCredit == null
                    || earnedCredit.compareTo(possibleCredit) <= 0;
        }
    }
}
