package com.dawnrise.academic.studentattendance.recording.dto;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StudentAttendanceRecordRequest(
        @NotNull @Positive Long studentEnrollmentId,
        @Min(0) Long expectedRecordVersion,
        @NotNull AttendanceStatus recordedStatus,
        @Size(max = 500) String remarks
) {
}
