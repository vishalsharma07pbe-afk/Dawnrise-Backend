package com.dawnrise.academic.studentattendance.reporting.projection;

import java.math.BigDecimal;

public interface GradeSectionAttendanceAggregateProjection {

    Long getSectionId();

    String getSectionCode();

    String getSectionName();

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