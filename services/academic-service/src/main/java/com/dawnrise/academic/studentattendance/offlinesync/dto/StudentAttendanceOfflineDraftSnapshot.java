package com.dawnrise.academic.studentattendance.offlinesync.dto;

import java.util.List;

public record StudentAttendanceOfflineDraftSnapshot(
        Long baseSessionVersion,
        List<StudentAttendanceOfflineDraftRosterEntry> roster
) {
}
