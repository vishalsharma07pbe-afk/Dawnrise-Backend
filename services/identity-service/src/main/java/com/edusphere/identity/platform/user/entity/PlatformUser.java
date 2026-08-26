package com.edusphere.identity.platform.user.entity;

import com.edusphere.identity.platform.user.enums.PlatformRole;
import com.edusphere.identity.platform.user.enums.PlatformUserStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "platform_users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_platform_users_username",
                        columnNames = "username"
                ),
                @UniqueConstraint(
                        name = "uk_platform_users_email",
                        columnNames = "email"
                )
        }
)
public class PlatformUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "platform_user_roles",
            joinColumns = @JoinColumn(name = "platform_user_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 60)
    private Set<PlatformRole> roles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlatformUserStatus status =
            PlatformUserStatus.PENDING_ACTIVATION;

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

    public PlatformUser() {
    }

    public PlatformUser(
            String username,
            String firstName,
            String email,
            Set<PlatformRole> roles
    ) {
        this.username = username;
        this.firstName = firstName;
        this.email = email;
        setRoles(roles);
        this.status = PlatformUserStatus.PENDING_ACTIVATION;
    }

    public Long getId() {
        return id;
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

    public Set<PlatformRole> getRoles() {
        return roles;
    }

    public void setRoles(Set<PlatformRole> roles) {
        this.roles = roles == null
                ? new HashSet<>()
                : new HashSet<>(roles);
    }

    public PlatformUserStatus getStatus() {
        return status;
    }

    public void setStatus(PlatformUserStatus status) {
        this.status = status;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public int getLoginLockLevel() {
        return loginLockLevel;
    }

    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
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

    public void addRole(PlatformRole role) {
        if (role != null) {
            roles.add(role);
        }
    }

    public void removeRole(PlatformRole role) {
        if (role != null) {
            roles.remove(role);
        }
    }

    public boolean hasRole(PlatformRole role) {
        return role != null && roles.contains(role);
    }

    public void recordFailedLoginAttempt() {
        failedLoginAttempts++;
    }

    public void lockLoginUntil(OffsetDateTime lockedUntil) {
        loginLockLevel++;
        failedLoginAttempts = 0;
        this.lockedUntil = lockedUntil;
    }

    public boolean isLoginLockedAt(OffsetDateTime currentTime) {
        return lockedUntil != null
                && lockedUntil.isAfter(currentTime);
    }

    public void clearLoginLock() {
        failedLoginAttempts = 0;
        loginLockLevel = 0;
        lockedUntil = null;
    }

    public void activate(String passwordHash) {
        if (status != PlatformUserStatus.PENDING_ACTIVATION) {
            throw new IllegalStateException(
                    "Only a pending platform account can be activated"
            );
        }

        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Password hash is required for activation"
            );
        }

        this.passwordHash = passwordHash;
        this.passwordChangedAt = OffsetDateTime.now();
        this.status = PlatformUserStatus.ACTIVE;
        clearLoginLock();
    }

    public void resetPassword(String passwordHash) {
        if (status != PlatformUserStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only an active platform account can reset its password"
            );
        }

        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Password hash is required for password reset"
            );
        }

        this.passwordHash = passwordHash;
        this.passwordChangedAt = OffsetDateTime.now();
        clearLoginLock();
    }
}