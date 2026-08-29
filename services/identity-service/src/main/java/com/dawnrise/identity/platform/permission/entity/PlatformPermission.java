package com.dawnrise.identity.platform.permission.entity;

import com.dawnrise.identity.platform.permission.enums.PlatformPermissionCode;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "platform_permissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_platform_permissions_code",
                        columnNames = "code"
                )
        }
)
public class PlatformPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private PlatformPermissionCode code;

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
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PlatformPermission() {
    }

    public Long getId() {
        return id;
    }

    public PlatformPermissionCode getCode() {
        return code;
    }

    public boolean isActive() {
        return active;
    }
}
