package com.dawnrise.identity.notification.exception;

import com.dawnrise.identity.common.exception.ResourceNotFoundException;

public class NotificationNotFoundException extends ResourceNotFoundException {
    public NotificationNotFoundException() { super("Notification not found"); }
}
