package com.dawnrise.academic.studentenrollment.integration.identity;

public class IdentityStudentEligibilityException
        extends RuntimeException {

    public IdentityStudentEligibilityException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}