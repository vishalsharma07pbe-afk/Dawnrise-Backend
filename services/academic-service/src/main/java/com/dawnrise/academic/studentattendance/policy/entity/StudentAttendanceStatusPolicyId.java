package com.dawnrise.academic.studentattendance.policy.entity;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;

import java.io.Serializable;
import java.util.Objects;

public class StudentAttendanceStatusPolicyId implements Serializable {

    private Long organizationId;
    private AttendanceStatus attendanceStatus;

    public StudentAttendanceStatusPolicyId() {
    }

    public StudentAttendanceStatusPolicyId(
            Long organizationId,
            AttendanceStatus attendanceStatus
    ) {
        this.organizationId = organizationId;
        this.attendanceStatus = attendanceStatus;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof StudentAttendanceStatusPolicyId other)) {
            return false;
        }
        return Objects.equals(organizationId, other.organizationId)
                && attendanceStatus == other.attendanceStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationId, attendanceStatus);
    }
}
