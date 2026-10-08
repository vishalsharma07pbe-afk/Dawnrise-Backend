package com.dawnrise.identity.auth.refreshtoken.model;
import java.util.UUID;
public record RefreshTokenCreationResult(String rawRefreshToken, UUID tokenFamilyId) {}
