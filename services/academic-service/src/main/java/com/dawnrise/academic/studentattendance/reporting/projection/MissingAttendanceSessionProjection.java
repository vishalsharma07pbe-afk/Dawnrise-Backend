package com.dawnrise.academic.studentattendance.reporting.projection;

import java.time.LocalDate;

public interface MissingAttendanceSessionProjection {

    Long getAcademicCalendarDayId();

    LocalDate getAttendanceDate();

    Long getGradeLevelId();

    Long getSectionId();

    String getSectionCode();

    String getSectionName();

    Long getAttendanceSessionId();

    String getLifecycleStatus();

    Long getEligibleStudentCount();

    Long getRecordedStudentCount();
}