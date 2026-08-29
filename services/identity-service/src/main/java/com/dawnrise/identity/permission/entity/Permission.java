package com.dawnrise.identity.permission.entity;

import com.dawnrise.identity.permission.enums.PermissionCode;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "permissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_permissions_code",
                        columnNames = "code"
                )
        }
)
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private PermissionCode code;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(
            name = "owning_service",
            nullable = false,
            length = 100
    )
    private String owningService;

    @Column(nullable = false)
    private boolean sensitive;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(
            name = "updated_at",
            nullable = false
    )
    private OffsetDateTime updatedAt;

    public Permission() {
    }

    public Permission(
            PermissionCode code,
            String description,
            String owningService,
            boolean sensitive
    ) {
        this.code = code;
        this.description = description;
        this.owningService = owningService;
        this.sensitive = sensitive;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public PermissionCode getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getOwningService() {
        return owningService;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void updateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "Permission description is required"
            );
        }

        this.description = description;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}