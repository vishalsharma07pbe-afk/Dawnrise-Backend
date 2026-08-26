package com.edusphere.identity.platform.permission.entity;

import com.edusphere.identity.platform.user.enums.PlatformRole;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "platform_role_permissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_platform_role_permissions_role_permission",
                        columnNames = {
                                "role",
                                "permission_id"
                        }
                )
        }
)
public class PlatformRolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private PlatformRole role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "permission_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_platform_role_permissions_permission"
            )
    )
    private PlatformPermission permission;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    protected PlatformRolePermission() {
    }
}
