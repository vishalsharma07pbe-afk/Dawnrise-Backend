package com.dawnrise.identity.platform.auth.refreshtoken.model;

import java.util.UUID;

public record PlatformRefreshTokenSession(Long platformUserId, UUID tokenFamilyId) {
}
