package com.dawnrise.identity.roleapproval.exception;

public class ApprovalNotAllowedException extends RuntimeException {
    public ApprovalNotAllowedException(String message) {
        super(message);
    }
}
