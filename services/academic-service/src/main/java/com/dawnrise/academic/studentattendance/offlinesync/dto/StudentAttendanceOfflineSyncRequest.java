package com.dawnrise.academic.studentattendance.offlinesync.dto;

import com.dawnrise.academic.studentattendance.offlinesync.enums.StudentAttendanceOfflineSyncMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record StudentAttendanceOfflineSyncRequest(
        @NotNull @Positive Long academicYearId,
        @NotNull @Positive Long gradeLevelId,
        @NotNull @Positive Long sectionId,
        @NotNull LocalDate attendanceDate,
        @PositiveOrZero Long baseSessionVersion,
        @NotNull StudentAttendanceOfflineSyncMode syncMode,
        @NotEmpty @Size(max = 500) List<@NotNull @Positive Long> expectedRosterEnrollmentIds,
        @NotEmpty @Size(max = 100)
        List<@Valid StudentAttendanceOfflineSyncRecordRequest> records
) {
}
