package com.dawnrise.academic.studentattendance.reporting.exception;

public class InvalidStudentAttendanceReportException
        extends RuntimeException {

    public InvalidStudentAttendanceReportException(
            String message
    ) {
        super(message);
    }

    public InvalidStudentAttendanceReportException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}