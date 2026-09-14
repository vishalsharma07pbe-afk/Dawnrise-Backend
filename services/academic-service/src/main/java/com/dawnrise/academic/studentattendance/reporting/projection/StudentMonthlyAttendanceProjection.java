package com.dawnrise.academic.studentattendance.reporting.projection;

import java.math.BigDecimal;

public interface StudentMonthlyAttendanceProjection {

    Long getPresentCount();

    Long getAbsentCount();

    Long getLateCount();

    Long getHalfDayCount();

    Long getExcusedCount();

    BigDecimal getEarnedCredit();

    BigDecimal getPossibleCredit();

    Long getSubmittedAttendanceDays();
}