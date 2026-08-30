package com.dawnrise.school.school.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class SchoolOnboardingRequest {

    @NotBlank(message = "School code is required")
    @Size(max = 50, message = "School code can't exceed 50 characters")
    private String schoolCode;

    @NotBlank(message = "School name can't be empty")
    @Size(max = 150, message = "School name can't exceed 150 characters")
    private String name;

    @NotBlank(message = "Email can't be empty")
    @Email(message = "Email must be valid")
    @Size(max = 150, message = "Email can't exceed 150 characters")
    private String email;

    @NotBlank(message = "Phone number can't be empty")
    @Pattern(
            regexp = "^[0-9+() -]{7,20}$",
            message = "Phone number must be valid"
    )
    private String phone;

    @Size(max = 500, message = "Address cannot exceed 500 characters")
    private String address;

    @Size(max = 180, message = "Motto cannot exceed 180 characters")
    private String motto;

    @Size(max = 240, message = "Tagline cannot exceed 240 characters")
    private String tagline;

    @Valid
    @NotNull(message = "Initial authority is required")
    private InitialAuthorityRequest initialAuthority;

    public String getSchoolCode() { return schoolCode; }
    public void setSchoolCode(String schoolCode) { this.schoolCode = schoolCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getMotto() { return motto; }
    public void setMotto(String motto) { this.motto = motto; }
    public String getTagline() { return tagline; }
    public void setTagline(String tagline) { this.tagline = tagline; }
    public InitialAuthorityRequest getInitialAuthority() { return initialAuthority; }
    public void setInitialAuthority(InitialAuthorityRequest initialAuthority) {
        this.initialAuthority = initialAuthority;
    }
}
