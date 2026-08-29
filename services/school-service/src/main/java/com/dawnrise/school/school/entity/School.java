package com.dawnrise.school.school.entity;

import com.dawnrise.school.school.enums.SchoolStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.OffsetDateTime;

@Entity
@Table(name = "schools")
public class School {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_code", unique = true, nullable = false, length = 50)
    private String schoolCode;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "address", length = 500)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SchoolStatus status = SchoolStatus.PENDING_PROVISIONING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public School(String schoolCode, String name, String email, String phone, String address, SchoolStatus status) {
        this.schoolCode = schoolCode;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.status = status;
    }

    public School() {
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public SchoolStatus getStatus() {
        return status;
    }

    public void markProvisioningPending() {
        this.status = SchoolStatus.PENDING_PROVISIONING;
    }

    public void activate() {
        this.status = SchoolStatus.ACTIVE;
    }

    public void markProvisioningFailed() {
        this.status = SchoolStatus.PROVISIONING_FAILED;
    }

    public void deactivate() {
        this.status = SchoolStatus.INACTIVE;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSchoolCode() {
        return schoolCode;
    }

    public void setSchoolCode(String schoolCode) {
        this.schoolCode = schoolCode;
    }

    public Long getId() {
        return id;
    }
}
