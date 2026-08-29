package com.dawnrise.school.school.repository;

import com.dawnrise.school.school.entity.SchoolProvisioning;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface SchoolProvisioningRepository
        extends JpaRepository<SchoolProvisioning, Long> {

    Optional<SchoolProvisioning> findBySchoolId(Long schoolId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SchoolProvisioning> findWithLockBySchoolId(Long schoolId);
}
