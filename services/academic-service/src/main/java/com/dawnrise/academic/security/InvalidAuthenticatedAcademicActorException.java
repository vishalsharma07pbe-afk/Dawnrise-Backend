package com.dawnrise.academic.security;

public class InvalidAuthenticatedAcademicActorException
        extends RuntimeException {

    public InvalidAuthenticatedAcademicActorException(String message) {
        super(message);
    }
}
