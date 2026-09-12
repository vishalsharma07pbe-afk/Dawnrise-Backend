package com.dawnrise.academic.studentattendance.policy.repository;

import com.dawnrise.academic.studentattendance.policy.entity.StudentAttendancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentAttendancePolicyRepository
        extends JpaRepository<StudentAttendancePolicy, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE StudentAttendancePolicy policy
            SET policy.version = policy.version + 1,
                policy.updatedAt = CURRENT_TIMESTAMP
            WHERE policy.organizationId = :organizationId
              AND policy.version = :expectedVersion
            """)
    int incrementVersionIfCurrent(
            @Param("organizationId") Long organizationId,
            @Param("expectedVersion") Long expectedVersion
    );
}
