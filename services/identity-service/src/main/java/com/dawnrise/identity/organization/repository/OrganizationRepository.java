package com.dawnrise.identity.organization.repository;

import com.dawnrise.identity.organization.entity.Organization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrganizationRepository
        extends JpaRepository<Organization, Long> {

    Optional<Organization> findBySchoolCode(
            String schoolCode
    );

    Optional<Organization> findBySchoolCodeIgnoreCase(
            String schoolCode
    );

    boolean existsBySchoolCode(
            String schoolCode
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select organization
            from Organization organization
            where organization.id = :organizationId
            """)
    Optional<Organization> findByIdForUpdate(
            @Param("organizationId") Long organizationId
    );
}