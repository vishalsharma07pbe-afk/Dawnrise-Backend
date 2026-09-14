package com.dawnrise.academic.studentattendance.reporting.dto;

public record GradeAttendanceSectionRowResponse(
        Long sectionId,
        String sectionCode,
        String sectionName,
        long submittedSessionCount,
        long distinctStudentCount,
        AttendanceSummary summary
) {

    public GradeAttendanceSectionRowResponse {
        requirePositive(sectionId, "Section ID");
        if (sectionCode == null || sectionCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Section code is required"
            );
        }

        if (sectionName == null || sectionName.isBlank()) {
            throw new IllegalArgumentException(
                    "Section name is required"
            );
        }

        if (submittedSessionCount < 0
                || distinctStudentCount < 0) {
            throw new IllegalArgumentException(
                    "Section report counts cannot be negative"
            );
        }

        sectionCode = sectionCode.trim();
        sectionName = sectionName.trim();
    }

    private static void requirePositive(
            Long value,
            String fieldName
    ) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }
    }
}
