package com.dawnrise.identity.user.repository;

import com.dawnrise.identity.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import java.util.Collection;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByOrganizationIdAndUsername(
            Long organizationId,
            String username
    );

    Optional<User> findByOrganizationIdAndEmail(
            Long organizationId,
            String email
    );

    Optional<User> findByOrganizationIdAndId(
            Long organizationId,
            Long userId
    );

    Optional<User> findByOrganizationIdAndEmailIgnoreCase(
            Long organizationId,
            String email
    );

    boolean existsByOrganizationIdAndEmailIgnoreCase(
            Long organizationId,
            String email
    );

    boolean existsByOrganizationIdAndUsername(
            Long organizationId,
            String username
    );

    boolean existsByOrganizationIdAndEmail(
            Long organizationId,
            String email
    );

    Page<User> findAllByOrganizationId(
            Long organizationId,
            Pageable pageable
    );

    @Query("""
            select count(user)
            from User user
            join user.roles role
            where user.organizationId = :organizationId
                and user.status = :status
                and role = :role
            """)
    long countByOrganizationIdAndStatusAndRole(
            @Param("organizationId") Long organizationId,
            @Param("status") UserStatus status,
            @Param("role") UserRole role
    );

    @Query("""
            select count(user)
            from User user
            join user.roles role
            where user.organizationId = :organizationId
                and role = :role
            """)
    long countByOrganizationIdAndRole(
            @Param("organizationId") Long organizationId,
            @Param("role") UserRole role
    );

    List<User> findAllByOrganizationIdAndIdIn(
            Long organizationId,
            Collection<Long> userIds
    );
}