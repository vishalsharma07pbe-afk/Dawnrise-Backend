package com.dawnrise.academic.studentattendance.discovery.service;

import com.dawnrise.academic.studentattendance.discovery.dto.AccessibleStudentAttendanceSectionResponse;

import java.util.List;

public interface StudentAttendanceAccessibleSectionService {

    List<AccessibleStudentAttendanceSectionResponse> listAccessibleSections(
            long organizationId,
            long actorUserId
    );
}
