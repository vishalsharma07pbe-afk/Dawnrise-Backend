package com.dawnrise.academic.studentattendance.policy.exception;

public class StudentAttendancePolicyNotFoundException
        extends RuntimeException {

    public StudentAttendancePolicyNotFoundException(String message) {
        super(message);
    }
}
