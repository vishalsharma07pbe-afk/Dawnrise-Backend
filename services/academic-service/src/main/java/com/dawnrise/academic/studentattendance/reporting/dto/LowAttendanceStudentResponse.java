package com.dawnrise.academic.studentattendance.reporting.dto;

public record LowAttendanceStudentResponse(
        Long studentEnrollmentId,
        Long studentUserId,
        Long gradeLevelId,
        Long sectionId,
        String rollNumber,
        AttendanceSummary summary
) {

    public LowAttendanceStudentResponse {
        requirePositive(studentEnrollmentId, "Student enrollment ID");
        requirePositive(studentUserId, "Student user ID");
        requirePositive(gradeLevelId, "Grade level ID");
        requirePositive(sectionId, "Section ID");
        if (rollNumber == null || rollNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "Student roll number is required"
            );
        }
        if (summary == null) {
            throw new IllegalArgumentException(
                    "Attendance summary is required"
            );
        }

        rollNumber = rollNumber.trim();
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
