package com.dawnrise.academic.studentattendance.reporting.projection;

import java.math.BigDecimal;

public interface LowAttendanceStudentProjection {

    Long getStudentEnrollmentId();

    Long getStudentUserId();

    Long getGradeLevelId();

    Long getSectionId();

    String getRollNumber();

    Long getPresentCount();

    Long getAbsentCount();

    Long getLateCount();

    Long getHalfDayCount();

    Long getExcusedCount();

    BigDecimal getEarnedCredit();

    BigDecimal getPossibleCredit();

    BigDecimal getAttendancePercentage();
}