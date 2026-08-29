package com.dawnrise.identity.permission.entity;

import com.dawnrise.identity.user.enums.UserRole;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "role_permissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_role_permissions_role_permission",
                        columnNames = {
                                "role",
                                "permission_id"
                        }
                )
        }
)
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private UserRole role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "permission_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_role_permissions_permission"
            )
    )
    private Permission permission;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    protected RolePermission() {
    }

    public RolePermission(
            UserRole role,
            Permission permission
    ) {
        if (role == null) {
            throw new IllegalArgumentException(
                    "Role is required"
            );
        }

        if (permission == null) {
            throw new IllegalArgumentException(
                    "Permission is required"
            );
        }

        this.role = role;
        this.permission = permission;
    }

    public Long getId() {
        return id;
    }

    public UserRole getRole() {
        return role;
    }

    public Permission getPermission() {
        return permission;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}