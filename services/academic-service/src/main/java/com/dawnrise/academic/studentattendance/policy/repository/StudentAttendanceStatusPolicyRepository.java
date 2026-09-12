package com.dawnrise.academic.studentattendance.policy.repository;

import com.dawnrise.academic.studentattendance.policy.entity.StudentAttendanceStatusPolicy;
import com.dawnrise.academic.studentattendance.policy.entity.StudentAttendanceStatusPolicyId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentAttendanceStatusPolicyRepository
        extends JpaRepository<StudentAttendanceStatusPolicy, StudentAttendanceStatusPolicyId> {

    List<StudentAttendanceStatusPolicy> findAllByOrganizationId(Long organizationId);
}
