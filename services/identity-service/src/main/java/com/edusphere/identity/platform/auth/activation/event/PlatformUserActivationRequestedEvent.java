package com.edusphere.identity.platform.auth.activation.event;

public record PlatformUserActivationRequestedEvent(
        Long platformUserId
) {
}