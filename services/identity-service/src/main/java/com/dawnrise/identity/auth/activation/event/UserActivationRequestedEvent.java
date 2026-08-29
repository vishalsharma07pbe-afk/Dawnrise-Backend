package com.dawnrise.identity.auth.activation.event;

public record UserActivationRequestedEvent(
        Long userId
) {
}