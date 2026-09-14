package com.dawnrise.academic.studentattendance.discovery.dto;

public record AccessibleStudentAttendanceSectionResponse(
        Long academicYearId,
        String academicYearName,
        Long gradeLevelId,
        String gradeLevelCode,
        String gradeLevelName,
        Long sectionId,
        String sectionCode,
        String sectionName
) {
}
