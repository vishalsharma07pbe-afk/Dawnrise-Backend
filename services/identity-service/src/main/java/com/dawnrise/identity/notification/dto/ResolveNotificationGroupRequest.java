package com.dawnrise.identity.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ResolveNotificationGroupRequest(
        @Positive long organizationId,
        @NotBlank @Size(max = 160) String groupingKey,
        @NotBlank @Size(max = 160) String excludeSourceEventId
) {
}
