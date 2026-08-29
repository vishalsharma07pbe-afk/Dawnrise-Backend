package com.dawnrise.identity.roleapproval.mapper;

import com.dawnrise.identity.roleapproval.dto.CreateRoleAssignmentRequest;
import com.dawnrise.identity.roleapproval.dto.RoleAssignmentRequestResponse;
import com.dawnrise.identity.roleapproval.entity.RoleAssignmentRequest;
import org.springframework.stereotype.Component;

@Component
public class RoleAssignmentRequestMapper {

    public RoleAssignmentRequest toEntity(
            Long organizationId,
            Long requestedByUserId,
            CreateRoleAssignmentRequest request
    ) {
        return new RoleAssignmentRequest(
                organizationId,
                request.getUserId(),
                request.getRequestedRole(),
                requestedByUserId,
                request.getReason()
        );
    }

    public RoleAssignmentRequestResponse toResponse(
            RoleAssignmentRequest request
    ) {
        return new RoleAssignmentRequestResponse(
                request.getId(),
                request.getOrganizationId(),
                request.getUserId(),
                request.getRequestedRole(),
                request.getRequestedByUserId(),
                request.getReason(),
                request.getStatus(),
                request.getCompletedAt(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }
}