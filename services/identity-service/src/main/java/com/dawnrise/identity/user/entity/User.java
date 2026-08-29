package com.dawnrise.identity.user.entity;

import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_users_organization_username",
                        columnNames = {"organization_id", "username"}
                )
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private Set<UserRole> roles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status = UserStatus.PENDING_ACTIVATION;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "login_lock_level", nullable = false)
    private int loginLockLevel = 0;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "password_changed_at")
    private OffsetDateTime passwordChangedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public User() {
    }

    public User(
            Long organizationId,
            String username,
            String firstName,
            Set<UserRole> roles
    ) {
        this.organizationId = organizationId;
        this.username = username;
        this.firstName = firstName;
        setRoles(roles);
        this.status = UserStatus.PENDING_ACTIVATION;
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
        this.organizationId = organizationId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
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

    public Set<UserRole> getRoles() {
        return roles;
    }

    public void setRoles(Set<UserRole> roles) {
        this.roles = roles == null
                ? new HashSet<>()
                : new HashSet<>(roles);
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public int getLoginLockLevel() {
        return loginLockLevel;
    }

    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(OffsetDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public void recordFailedLoginAttempt() {
        failedLoginAttempts++;
    }

    public void lockLoginUntil(
            OffsetDateTime lockedUntil
    ) {
        this.loginLockLevel++;
        this.failedLoginAttempts = 0;
        this.lockedUntil = lockedUntil;
    }

    public boolean isLoginLockedAt(OffsetDateTime currentTime) {
        return lockedUntil != null
                && lockedUntil.isAfter(currentTime);
    }

    public void clearLoginLock() {
        this.failedLoginAttempts = 0;
        this.loginLockLevel = 0;
        this.lockedUntil = null;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(OffsetDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public OffsetDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void addRole(UserRole role) {
        if (role != null) {
            roles.add(role);
        }
    }

    public void removeRole(UserRole role) {
        if (role != null) {
            roles.remove(role);
        }
    }

    public boolean hasRole(UserRole role) {
        return role != null && roles.contains(role);
    }

    public void activate(String passwordHash) {
        if (status != UserStatus.PENDING_ACTIVATION) {
            throw new IllegalStateException(
                    "Only a pending account can be activated"
            );
        }

        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Password hash is required for activation"
            );
        }

        this.passwordHash = passwordHash;
        this.passwordChangedAt = OffsetDateTime.now();
        this.status = UserStatus.ACTIVE;
        this.failedLoginAttempts = 0;
        this.loginLockLevel = 0;
        this.lockedUntil = null;
    }

    public void resetPassword(String passwordHash) {
        if (status != UserStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only an active account can reset its password"
            );
        }

        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Password hash is required for password reset"
            );
        }

        this.passwordHash = passwordHash;
        this.passwordChangedAt = OffsetDateTime.now();
        this.failedLoginAttempts = 0;
        this.loginLockLevel = 0;
        this.lockedUntil = null;
    }
}
