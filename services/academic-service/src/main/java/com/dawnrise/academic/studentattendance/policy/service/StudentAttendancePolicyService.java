package com.dawnrise.academic.studentattendance.policy.service;

import com.dawnrise.academic.studentattendance.policy.dto.StudentAttendancePolicyRequest;
import com.dawnrise.academic.studentattendance.policy.dto.StudentAttendancePolicyResponse;

public interface StudentAttendancePolicyService {
    StudentAttendancePolicyResponse get(long organizationId);
    StudentAttendancePolicyResponse initialize(
            long organizationId,
            long actorUserId
    );
    StudentAttendancePolicyResponse update(
            long organizationId,
            long actorUserId,
            StudentAttendancePolicyRequest request
    );
}
