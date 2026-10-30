package com.dawnrise.identity.notification.dto;

import com.dawnrise.identity.notification.model.NotificationActionType;
import jakarta.validation.constraints.Positive;

public record NotificationAction(
        NotificationActionType type,
        @Positive Long referenceId,
        @Positive Long studentUserId,
        @Positive Long academicYearId
) {
    public NotificationAction {
        type = type == null ? NotificationActionType.NONE : type;
    }
}
