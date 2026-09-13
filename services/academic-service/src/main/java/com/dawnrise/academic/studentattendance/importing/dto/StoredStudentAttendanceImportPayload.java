package com.dawnrise.academic.studentattendance.importing.dto;

import java.util.List;

public record StoredStudentAttendanceImportPayload(
        StudentAttendanceImportPreviewResponse preview,
        List<Long> expectedRosterEnrollmentIds
) {
}
