package com.dawnrise.identity.studentguardian.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class EndStudentGuardianRelationshipRequest {

    @Positive
    private Long replacementPrimaryRelationshipId;

    @Size(max = 500)
    private String endReason;

    public Long getReplacementPrimaryRelationshipId() {
        return replacementPrimaryRelationshipId;
    }

    public void setReplacementPrimaryRelationshipId(
            Long replacementPrimaryRelationshipId
    ) {
        this.replacementPrimaryRelationshipId =
                replacementPrimaryRelationshipId;
    }

    public String getEndReason() {
        return endReason;
    }

    public void setEndReason(String endReason) {
        this.endReason = endReason;
    }
}
