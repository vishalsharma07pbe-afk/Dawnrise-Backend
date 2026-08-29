package com.dawnrise.school.school.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateSchoolRequest {

    @NotBlank(message = "School name is required")
    @Size(max = 150, message = "School name can't exceed 150 characters")
    private String name;

    @Email(message = "Email must be valid")
    @Size(max = 150, message = "Email can't exceed 150 characters")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^[0-9+() -]{7,20}$",
            message = "Phone number must be valid"
    )
    private String phone;

    @Size(max = 500, message = "Address cannot exceed 500 characters")
    private String address;

    public UpdateSchoolRequest() {
    }

    public UpdateSchoolRequest(
            String name,
            String email,
            String phone,
            String address
    ) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.address = address;
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