package com.dawnrise.identity.auth.session.exception;

public class RecentAuthenticationRequiredException extends RuntimeException {

    public RecentAuthenticationRequiredException(String message) {
        super(message);
    }
}
