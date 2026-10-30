package com.dawnrise.identity.notification.securityevent;

public record IdentitySecurityNotificationEvent(long organizationId,long userId,Kind kind,String sourceEventId) {
    public enum Kind { PASSWORD_CHANGED, PASSWORD_RESET_COMPLETED, ACCOUNT_ACTIVATED, ACCOUNT_LOCKED, ACCOUNT_STATUS_CHANGED }
}
