package com.dawnrise.academic.studentattendance.reporting.exception;

public class StudentAttendanceReportNotFoundException
        extends RuntimeException {

    public StudentAttendanceReportNotFoundException(
            String message
    ) {
        super(message);
    }
}