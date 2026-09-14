package com.dawnrise.academic.studentattendance.reporting.projection;

import java.math.BigDecimal;

public interface DailyStudentAttendanceProjection {

    Long getAttendanceRecordId();

    Long getStudentEnrollmentId();

    Long getStudentUserId();

    String getRollNumber();

    String getRecordedStatus();

    String getEffectiveStatus();

    BigDecimal getEarnedCredit();

    BigDecimal getPossibleCredit();

    Boolean getLatePenaltyApplied();

    String getRemarks();
}