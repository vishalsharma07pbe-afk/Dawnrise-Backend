package com.dawnrise.academic.results.integration.identity;

public interface IdentityGuardianAccessClient {
    boolean hasActiveRelationship(long organizationId, long guardianUserId, long studentUserId);
}
