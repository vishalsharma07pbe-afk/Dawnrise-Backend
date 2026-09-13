package com.dawnrise.academic.studentattendance.offlinesync.dto;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record StudentAttendanceOfflineSyncRecordRequest(
        @NotNull @Positive Long studentEnrollmentId,
        @PositiveOrZero Long expectedRecordVersion,
        @NotNull AttendanceStatus recordedStatus,
        @Size(max = 500) String remarks
) {
}
