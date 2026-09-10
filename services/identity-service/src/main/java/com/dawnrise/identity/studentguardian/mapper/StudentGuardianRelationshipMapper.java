package com.dawnrise.identity.studentguardian.mapper;

import com.dawnrise.identity.studentguardian.dto.StudentGuardianRelationshipResponse;
import com.dawnrise.identity.studentguardian.dto.StudentGuardianUserSummaryResponse;
import com.dawnrise.identity.studentguardian.entity.StudentGuardianRelationship;
import com.dawnrise.identity.studentguardian.repository.StudentGuardianRelationshipView;
import com.dawnrise.identity.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class StudentGuardianRelationshipMapper {

    public StudentGuardianRelationshipResponse toResponse(
            StudentGuardianRelationshipView view
    ) {
        StudentGuardianRelationship relationship = view.relationship();

        return new StudentGuardianRelationshipResponse(
                relationship.getId(),
                relationship.getOrganizationId(),
                toSummary(view.student()),
                toSummary(view.guardian()),
                relationship.getRelationshipType(),
                relationship.getStatus(),
                relationship.isPrimaryGuardian(),
                relationship.getStartedAt(),
                relationship.getEndedAt(),
                relationship.getCreatedByUserId(),
                relationship.getEndedByUserId(),
                relationship.getEndReason(),
                relationship.getVersion(),
                relationship.getCreatedAt(),
                relationship.getUpdatedAt()
        );
    }

    private StudentGuardianUserSummaryResponse toSummary(User user) {
        return new StudentGuardianUserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getMiddleName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus()
        );
    }
}
