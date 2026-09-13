package com.dawnrise.academic.studentattendance.importing.service;

import java.util.List;

public record ParsedStudentAttendanceImportRow(
        int rowNumber,
        String academicYear,
        String gradeCode,
        String sectionCode,
        String attendanceDate,
        String rollNumber,
        String attendanceStatus,
        String remarks,
        List<String> parsingErrors
) {
}
