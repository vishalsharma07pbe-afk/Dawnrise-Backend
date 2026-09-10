package com.dawnrise.identity.studentguardian.dto;

import com.dawnrise.identity.studentguardian.enums.StudentGuardianRelationshipType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class CreateStudentGuardianRelationshipRequest {

    @NotNull
    @Positive
    private Long guardianUserId;

    @NotNull
    private StudentGuardianRelationshipType relationshipType;

    private Boolean primaryGuardian;

    public Long getGuardianUserId() {
        return guardianUserId;
    }

    public void setGuardianUserId(Long guardianUserId) {
        this.guardianUserId = guardianUserId;
    }

    public StudentGuardianRelationshipType getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(
            StudentGuardianRelationshipType relationshipType
    ) {
        this.relationshipType = relationshipType;
    }

    public Boolean getPrimaryGuardian() {
        return primaryGuardian;
    }

    public void setPrimaryGuardian(Boolean primaryGuardian) {
        this.primaryGuardian = primaryGuardian;
    }
}
