package com.dawnrise.academic.studentattendance.notificationoutbox.service;

import com.dawnrise.academic.studentattendance.notificationoutbox.enums.StudentAttendanceNotificationEventType;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class StudentAttendanceNotificationClassifier {

    public Optional<StudentAttendanceNotificationEventType> classify(
            AttendanceStatus recordedStatus,
            AttendanceStatus effectiveStatus,
            boolean latePenaltyApplied
    ) {
        if (recordedStatus == null || effectiveStatus == null) {
            throw new IllegalArgumentException(
                    "Attendance statuses are required"
            );
        }

        if (latePenaltyApplied) {
            if (recordedStatus != AttendanceStatus.LATE
                    || (
                    effectiveStatus != AttendanceStatus.HALF_DAY
                            && effectiveStatus
                            != AttendanceStatus.ABSENT
            )) {
                throw new IllegalArgumentException(
                        "Late-penalty attendance snapshot is invalid"
                );
            }

            return Optional.of(
                    StudentAttendanceNotificationEventType
                            .LATE_PENALTY_APPLIED
            );
        }

        if (recordedStatus == AttendanceStatus.ABSENT
                || effectiveStatus == AttendanceStatus.ABSENT) {
            return Optional.of(
                    StudentAttendanceNotificationEventType
                            .ABSENCE_RECORDED
            );
        }

        if (recordedStatus == AttendanceStatus.LATE) {
            return Optional.of(
                    StudentAttendanceNotificationEventType
                            .LATE_RECORDED
            );
        }

        return Optional.empty();
    }

    public boolean notificationSnapshotChanged(
            AttendanceStatus previousRecordedStatus,
            AttendanceStatus previousEffectiveStatus,
            boolean previousLatePenaltyApplied,
            AttendanceStatus currentRecordedStatus,
            AttendanceStatus currentEffectiveStatus,
            boolean currentLatePenaltyApplied
    ) {
        return previousRecordedStatus != currentRecordedStatus
                || previousEffectiveStatus != currentEffectiveStatus
                || previousLatePenaltyApplied
                != currentLatePenaltyApplied;
    }
}