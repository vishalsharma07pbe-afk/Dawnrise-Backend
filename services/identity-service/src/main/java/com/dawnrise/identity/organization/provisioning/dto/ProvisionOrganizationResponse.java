package com.dawnrise.identity.organization.provisioning.dto;

import com.dawnrise.identity.organization.enums.OrganizationStatus;
import com.dawnrise.identity.organization.provisioning.enums.ProvisioningRequestStatus;
import com.dawnrise.identity.user.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ProvisionOrganizationResponse {

    private final Long organizationId;
    private final OrganizationStatus organizationStatus;
    private final Long authorityUserId;
    private final UserStatus authorityStatus;
    private final ProvisioningRequestStatus provisioningStatus;

    @JsonCreator
    public ProvisionOrganizationResponse(
            @JsonProperty("organizationId")
            Long organizationId,

            @JsonProperty("organizationStatus")
            OrganizationStatus organizationStatus,

            @JsonProperty("authorityUserId")
            Long authorityUserId,

            @JsonProperty("authorityStatus")
            UserStatus authorityStatus,

            @JsonProperty("provisioningStatus")
            ProvisioningRequestStatus provisioningStatus
    ) {
        this.organizationId = organizationId;
        this.organizationStatus = organizationStatus;
        this.authorityUserId = authorityUserId;
        this.authorityStatus = authorityStatus;
        this.provisioningStatus = provisioningStatus;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public OrganizationStatus getOrganizationStatus() {
        return organizationStatus;
    }

    public Long getAuthorityUserId() {
        return authorityUserId;
    }

    public UserStatus getAuthorityStatus() {
        return authorityStatus;
    }

    public ProvisioningRequestStatus getProvisioningStatus() {
        return provisioningStatus;
    }
}