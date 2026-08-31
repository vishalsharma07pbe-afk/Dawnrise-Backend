package com.dawnrise.identity.profilechange.exception;

public class ProfileChangeNotAllowedException extends RuntimeException {
    public ProfileChangeNotAllowedException(String message) {
        super(message);
    }
}
