package com.dawnrise.identity.organization.provisioning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class ProvisionOrganizationRequest {

    @NotNull(message = "Organization ID is required")
    @Positive(message = "Organization ID must be positive")
    private Long organizationId;

    @NotBlank(message = "School code is required")
    @Size(
            max = 50,
            message = "School code cannot exceed 50 characters"
    )
    private String schoolCode;

    @NotBlank(message = "School name is required")
    @Size(
            max = 150,
            message = "School name cannot exceed 150 characters"
    )
    private String schoolName;

    @Email(message = "School email must be valid")
    @Size(
            max = 150,
            message = "School email cannot exceed 150 characters"
    )
    private String schoolEmail;

    @Valid
    @NotNull(message = "Initial authority information is required")
    private InitialAuthorityRequest authority;

    public ProvisionOrganizationRequest() {
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(
            Long organizationId
    ) {
        this.organizationId = organizationId;
    }

    public String getSchoolCode() {
        return schoolCode;
    }

    public void setSchoolCode(
            String schoolCode
    ) {
        this.schoolCode = schoolCode;
    }

    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(
            String schoolName
    ) {
        this.schoolName = schoolName;
    }

    public String getSchoolEmail() {
        return schoolEmail;
    }

    public void setSchoolEmail(
            String schoolEmail
    ) {
        this.schoolEmail = schoolEmail;
    }

    public InitialAuthorityRequest getAuthority() {
        return authority;
    }

    public void setAuthority(
            InitialAuthorityRequest authority
    ) {
        this.authority = authority;
    }
}