package com.dawnrise.identity.platform.auth.activation.event;

public record PlatformUserActivationRequestedEvent(
        Long platformUserId
) {
}