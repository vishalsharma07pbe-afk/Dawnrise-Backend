package com.dawnrise.academic.results.integration.identity;

import java.util.List;

public interface IdentityGuardianAccessClient {
    boolean hasActiveRelationship(long organizationId, long guardianUserId, long studentUserId);

    List<Long> activeGuardianUserIds(long organizationId, long studentUserId);
}
