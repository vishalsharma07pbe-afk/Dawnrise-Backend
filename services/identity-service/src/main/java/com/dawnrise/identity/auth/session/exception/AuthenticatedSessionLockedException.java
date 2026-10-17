package com.dawnrise.identity.auth.session.exception;

public class AuthenticatedSessionLockedException extends RuntimeException {
    public AuthenticatedSessionLockedException(String message) { super(message); }
}
