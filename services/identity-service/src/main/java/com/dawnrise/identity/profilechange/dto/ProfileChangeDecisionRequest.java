package com.dawnrise.identity.profilechange.dto;

import jakarta.validation.constraints.Size;

public class ProfileChangeDecisionRequest {

    @Size(
            max = 500,
            message = "Decision reason cannot exceed 500 characters"
    )
    private String reason;

    public ProfileChangeDecisionRequest() {
    }

    public ProfileChangeDecisionRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}