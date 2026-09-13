package com.dawnrise.academic.studentattendance.importing.dto;

import com.dawnrise.academic.studentattendance.importing.enums.StudentAttendanceImportFormat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record StudentAttendanceImportPreviewResponse(
        Long previewId,
        String previewFingerprint,
        String originalFileName,
        StudentAttendanceImportFormat sourceFormat,
        boolean canConfirm,
        Long academicYearId,
        Long gradeLevelId,
        Long sectionId,
        LocalDate attendanceDate,
        Long baseSessionVersion,
        int rowCount,
        OffsetDateTime expiresAt,
        List<String> errors,
        List<StudentAttendanceImportRowResponse> rows
) {
}
