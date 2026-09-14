package com.dawnrise.academic.studentattendance.reporting.projection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

public interface StudentAttendanceHistoryProjection {

    Long getAttendanceSessionId();

    Long getAttendanceRecordId();

    Long getStudentEnrollmentId();

    Long getAcademicYearId();

    Long getGradeLevelId();

    Long getSectionId();

    LocalDate getAttendanceDate();

    String getRecordedStatus();

    String getEffectiveStatus();

    BigDecimal getEarnedCredit();

    BigDecimal getPossibleCredit();

    Boolean getLatePenaltyApplied();

    String getRemarks();

    String getSubmissionType();

    Instant getSubmittedAt();

    Instant getLastUpdatedAt();
}