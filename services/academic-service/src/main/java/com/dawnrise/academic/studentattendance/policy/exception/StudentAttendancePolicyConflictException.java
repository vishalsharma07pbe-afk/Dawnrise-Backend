package com.dawnrise.academic.studentattendance.policy.exception;

public class StudentAttendancePolicyConflictException
        extends RuntimeException {

    public StudentAttendancePolicyConflictException(String message) {
        super(message);
    }

    public StudentAttendancePolicyConflictException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
