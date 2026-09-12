package com.dawnrise.school.school.exception;

public class InvalidSchoolTimeZoneException extends RuntimeException {

    public InvalidSchoolTimeZoneException(String message) {
        super(message);
    }

    public InvalidSchoolTimeZoneException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
