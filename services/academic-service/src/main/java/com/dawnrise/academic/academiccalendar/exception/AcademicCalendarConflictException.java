package com.dawnrise.academic.academiccalendar.exception;

public class AcademicCalendarConflictException extends RuntimeException {

    public AcademicCalendarConflictException(String message) {
        super(message);
    }

    public AcademicCalendarConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
