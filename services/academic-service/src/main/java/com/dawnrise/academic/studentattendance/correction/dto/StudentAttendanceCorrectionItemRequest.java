package com.dawnrise.academic.studentattendance.correction.dto;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StudentAttendanceCorrectionItemRequest(
        @NotNull Long attendanceRecordId,
        @NotNull Long expectedRecordVersion,
        @NotNull AttendanceStatus proposedRecordedStatus,
        @Size(max = 500) String proposedRemarks
) {
}
