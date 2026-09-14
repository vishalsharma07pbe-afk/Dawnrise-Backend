package com.dawnrise.academic.studentattendance.reporting.security;

public interface StudentAttendanceReportAccessService {

    void requireLeadershipAccess();

    void requireSectionAccess(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            long actorUserId
    );

    boolean hasLeadershipAccess();
}