package com.dawnrise.academic.studentattendance.policy.enums;

import java.util.EnumSet;
import java.util.Set;

public enum AttendanceStatus {
    UNMARKED,
    PRESENT,
    ABSENT,
    LATE,
    HALF_DAY,
    EXCUSED;

    public static Set<AttendanceStatus> finalStatuses() {
        return EnumSet.of(
                PRESENT,
                ABSENT,
                LATE,
                HALF_DAY,
                EXCUSED
        );
    }
}
