package com.dawnrise.academic.studentattendance.importing.service;

import com.dawnrise.academic.studentattendance.importing.enums.StudentAttendanceImportFormat;

import java.util.List;

public record ParsedStudentAttendanceImport(
        String originalFileName,
        StudentAttendanceImportFormat format,
        List<ParsedStudentAttendanceImportRow> rows
) {
}
