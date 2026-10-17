package com.dawnrise.identity.auth.refreshtoken.model;

import java.util.UUID;

public record RefreshTokenSession(Long userId, UUID tokenFamilyId) {}
