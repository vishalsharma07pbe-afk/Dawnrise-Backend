package com.dawnrise.identity.studentguardian.dto;

import com.dawnrise.identity.user.enums.UserStatus;

public class StudentGuardianUserSummaryResponse {

    private Long id;
    private String username;
    private String firstName;
    private String middleName;
    private String lastName;
    private String email;
    private String phone;
    private UserStatus status;

    public StudentGuardianUserSummaryResponse() {
    }

    public StudentGuardianUserSummaryResponse(
            Long id,
            String username,
            String firstName,
            String middleName,
            String lastName,
            String email,
            String phone,
            UserStatus status
    ) {
        this.id = id;
        this.username = username;
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getFirstName() { return firstName; }
    public String getMiddleName() { return middleName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public UserStatus getStatus() { return status; }
}
