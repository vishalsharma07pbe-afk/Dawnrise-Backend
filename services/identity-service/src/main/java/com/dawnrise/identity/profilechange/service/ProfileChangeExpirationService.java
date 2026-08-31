package com.dawnrise.identity.profilechange.service;

import com.dawnrise.identity.profilechange.entity.ProfileChangeRequest;
import com.dawnrise.identity.profilechange.repository.ProfileChangeRequestRepository;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class ProfileChangeExpirationService {

    private final ProfileChangeRequestRepository requestRepository;
    private final SecurityAuditService auditService;

    public ProfileChangeExpirationService(
            ProfileChangeRequestRepository requestRepository,
            SecurityAuditService auditService
    ) {
        this.requestRepository = requestRepository;
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireIfNecessary(
            Long organizationId,
            UUID requestId,
            OffsetDateTime currentTime
    ) {
        ProfileChangeRequest request =
                requestRepository
                        .findByOrganizationIdAndPublicId(
                                organizationId,
                                requestId
                        )
                        .orElse(null);

        if (request == null
                || !request.isPending()
                || !request.isExpiredAt(currentTime)) {
            return false;
        }

        request.expire(currentTime);

        ProfileChangeRequest savedRequest =
                requestRepository.save(request);

        auditService.record(
                organizationId,
                null,
                SecurityAuditAction.PROFILE_CHANGE_REQUEST_EXPIRE,
                SecurityAuditOutcome.SUCCESS,
                "PROFILE_CHANGE_REQUEST",
                savedRequest.getId(),
                Map.of(
                        "publicRequestId",
                        savedRequest.getPublicId().toString(),
                        "targetUserId",
                        savedRequest.getTargetUserId()
                )
        );

        return true;
    }
}