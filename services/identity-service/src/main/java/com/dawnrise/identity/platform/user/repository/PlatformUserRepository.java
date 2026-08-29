package com.dawnrise.identity.platform.user.repository;

import com.dawnrise.identity.platform.user.entity.PlatformUser;
import com.dawnrise.identity.platform.user.enums.PlatformUserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlatformUserRepository
        extends JpaRepository<PlatformUser, Long> {

    Optional<PlatformUser> findByUsernameIgnoreCase(
            String username
    );

    Optional<PlatformUser> findByEmailIgnoreCase(
            String email
    );

    boolean existsByUsernameIgnoreCase(
            String username
    );

    boolean existsByEmailIgnoreCase(
            String email
    );

    @Query("""
            SELECT platformUser
            FROM PlatformUser platformUser
            WHERE (
                :status IS NULL
                OR platformUser.status = :status
            )
            AND (
                :search IS NULL
                OR LOWER(platformUser.username)
                    LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(platformUser.firstName)
                    LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(platformUser.middleName, ''))
                    LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(platformUser.lastName, ''))
                    LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(platformUser.email)
                    LIKE LOWER(CONCAT('%', :search, '%'))
            )
            """)
    Page<PlatformUser> searchPlatformUsers(
            @Param("status") PlatformUserStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}