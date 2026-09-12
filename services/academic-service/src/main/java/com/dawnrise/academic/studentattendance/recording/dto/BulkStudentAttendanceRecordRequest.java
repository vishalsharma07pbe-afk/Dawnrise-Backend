package com.dawnrise.academic.studentattendance.recording.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BulkStudentAttendanceRecordRequest(
        @NotNull List<@Valid StudentAttendanceRecordRequest> records
) {
}
