package com.dawnrise.academic.studentattendance.reporting.projection;

import java.math.BigDecimal;

public interface AttendanceAggregateProjection {

    Long getPresentCount();

    Long getAbsentCount();

    Long getLateCount();

    Long getHalfDayCount();

    Long getExcusedCount();

    BigDecimal getEarnedCredit();

    BigDecimal getPossibleCredit();

    Long getSubmittedSessionCount();

    Long getDistinctStudentCount();
}