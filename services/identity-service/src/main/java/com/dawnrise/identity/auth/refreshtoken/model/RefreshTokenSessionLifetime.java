package com.dawnrise.identity.auth.refreshtoken.model;

import java.time.Duration;

public record RefreshTokenSessionLifetime(
        Duration inactivityExpiration,
        Duration absoluteSessionLifetime
) {
}
