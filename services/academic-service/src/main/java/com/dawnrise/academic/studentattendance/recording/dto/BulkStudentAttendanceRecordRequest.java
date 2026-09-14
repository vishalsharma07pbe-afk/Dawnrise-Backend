package com.dawnrise.academic.studentattendance.recording.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BulkStudentAttendanceRecordRequest(
        @NotNull @Min(0) Long expectedSessionVersion,
        @NotNull List<@Valid StudentAttendanceRecordRequest> records
) {
}
