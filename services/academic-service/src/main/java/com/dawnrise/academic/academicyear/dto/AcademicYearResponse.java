package com.dawnrise.academic.academicyear.dto;

import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AcademicYearResponse(
        Long id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        AcademicYearStatus status,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}