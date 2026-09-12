package com.dawnrise.academic.common.integration.school;

public class SchoolTimeZoneUnavailableException extends RuntimeException {

    public SchoolTimeZoneUnavailableException(String message) {
        super(message);
    }

    public SchoolTimeZoneUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
