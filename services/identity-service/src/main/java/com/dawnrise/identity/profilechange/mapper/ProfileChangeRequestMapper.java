package com.dawnrise.identity.profilechange.mapper;

import com.dawnrise.identity.profilechange.dto.ProfileChangeRequestResponse;
import com.dawnrise.identity.profilechange.entity.ProfileChangeRequest;
import org.springframework.stereotype.Component;

@Component
public class ProfileChangeRequestMapper {

    public ProfileChangeRequestResponse toResponse(
            ProfileChangeRequest request,
            String requestedByName,
            String decidedByName
    ) {
        return new ProfileChangeRequestResponse(
                request.getPublicId(),
                requestedByName,
                decidedByName,
                request.getOriginalFirstName(),
                request.getOriginalMiddleName(),
                request.getOriginalLastName(),
                request.getOriginalEmail(),
                request.getOriginalPhone(),
                request.getProposedFirstName(),
                request.getProposedMiddleName(),
                request.getProposedLastName(),
                request.getProposedEmail(),
                request.getProposedPhone(),
                request.getReason(),
                request.getStatus(),
                request.getDecisionReason(),
                request.getDecidedAt(),
                request.getExpiresAt(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }
}