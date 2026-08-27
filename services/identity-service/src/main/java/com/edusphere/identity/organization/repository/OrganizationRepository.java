package com.edusphere.identity.organization.repository;

import com.edusphere.identity.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
