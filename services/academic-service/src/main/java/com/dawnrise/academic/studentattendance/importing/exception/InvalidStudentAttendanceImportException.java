package com.dawnrise.academic.studentattendance.importing.exception;

public class InvalidStudentAttendanceImportException extends RuntimeException {
    public InvalidStudentAttendanceImportException(String message) {
        super(message);
    }

    public InvalidStudentAttendanceImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
