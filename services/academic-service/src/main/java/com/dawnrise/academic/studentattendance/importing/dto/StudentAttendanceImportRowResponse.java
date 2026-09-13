package com.dawnrise.academic.studentattendance.importing.dto;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;

import java.time.LocalDate;
import java.util.List;

public record StudentAttendanceImportRowResponse(
        int rowNumber,
        String academicYear,
        String gradeCode,
        String sectionCode,
        LocalDate attendanceDate,
        String rollNumber,
        Long studentEnrollmentId,
        Long expectedRecordVersion,
        AttendanceStatus attendanceStatus,
        String remarks,
        List<String> errors
) {
}
