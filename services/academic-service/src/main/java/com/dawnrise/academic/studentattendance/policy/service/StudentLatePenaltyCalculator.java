package com.dawnrise.academic.studentattendance.policy.service;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.policy.enums.LatePenaltyOutcome;
import com.dawnrise.academic.studentattendance.policy.exception.InvalidStudentAttendancePolicyException;
import org.springframework.stereotype.Component;

@Component
public class StudentLatePenaltyCalculator {

    private static final int MAX_LATE_OCCURRENCES_THRESHOLD = 100;

    public AttendanceStatus effectiveStatus(
            AttendanceStatus markedStatus,
            int lateOccurrenceNumber,
            boolean latePenaltyEnabled,
            int lateOccurrencesThreshold,
            LatePenaltyOutcome latePenaltyOutcome
    ) {
        if (markedStatus == null) {
            throw new InvalidStudentAttendancePolicyException(
                    "Marked attendance status is required"
            );
        }
        if (latePenaltyOutcome == null) {
            throw new InvalidStudentAttendancePolicyException(
                    "Late penalty outcome is required"
            );
        }
        if (lateOccurrenceNumber <= 0) {
            throw new InvalidStudentAttendancePolicyException(
                    "Late occurrence number must be positive"
            );
        }
        if (lateOccurrencesThreshold <= 0
                || lateOccurrencesThreshold > MAX_LATE_OCCURRENCES_THRESHOLD) {
            throw new InvalidStudentAttendancePolicyException(
                    "Late occurrences threshold must be between 1 and 100"
            );
        }
        if (markedStatus != AttendanceStatus.LATE || !latePenaltyEnabled) {
            return markedStatus;
        }
        if (lateOccurrenceNumber % lateOccurrencesThreshold != 0) {
            return AttendanceStatus.LATE;
        }
        return switch (latePenaltyOutcome) {
            case HALF_DAY -> AttendanceStatus.HALF_DAY;
            case ABSENT -> AttendanceStatus.ABSENT;
        };
    }
}
