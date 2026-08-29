package com.dawnrise.school.school.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateSchoolRequest {

    @NotBlank(message = "School code is required")
    @Size(max = 50, message = "School code can't exceed 50 characters")
    private String schoolCode;

    @NotBlank(message = "School name can't be empty")
    @Size(max = 150, message = "School name can't exceed 150 characters")
    private String name;

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

    public CreateSchoolRequest() {
    }

    public CreateSchoolRequest(
            String schoolCode,
            String name,
            String email,
            String phone,
            String address
    ) {
        this.schoolCode = schoolCode;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.address = address;
    }

    public String getSchoolCode() {
        return schoolCode;
    }

    public void setSchoolCode(String schoolCode) {
        this.schoolCode = schoolCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}