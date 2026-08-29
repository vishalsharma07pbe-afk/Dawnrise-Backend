package com.dawnrise.identity.auth.exception;

public class PasswordChangeNotAllowedException extends RuntimeException {

    public PasswordChangeNotAllowedException(String message) {
        super(message);
    }
}
