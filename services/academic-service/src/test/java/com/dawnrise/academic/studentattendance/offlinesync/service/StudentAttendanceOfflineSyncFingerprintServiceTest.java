package com.dawnrise.academic.studentattendance.offlinesync.service;

import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncRecordRequest;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncRequest;
import com.dawnrise.academic.studentattendance.offlinesync.enums.StudentAttendanceOfflineSyncMode;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceOfflineSyncFingerprintServiceTest {

    private final StudentAttendanceOfflineSyncFingerprintService service =
            new StudentAttendanceOfflineSyncFingerprintService();

    @Test
    void sameNormalizedPayloadHasSameHash() {
        String first = service.requestHash(7, 11, request(" note "));
        String second = service.requestHash(7, 11, request("note"));

        assertThat(first).matches("^sha256:[0-9a-f]{64}$");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void actorAndRecordOrderArePartOfCanonicalRequest() {
        StudentAttendanceOfflineSyncRequest original = request("note");
        StudentAttendanceOfflineSyncRequest reversed =
                new StudentAttendanceOfflineSyncRequest(
                        original.academicYearId(), original.gradeLevelId(),
                        original.sectionId(), original.attendanceDate(),
                        original.baseSessionVersion(), original.syncMode(),
                        original.expectedRosterEnrollmentIds(),
                        List.of(original.records().get(1), original.records().get(0))
                );

        assertThat(service.requestHash(7, 12, original))
                .isNotEqualTo(service.requestHash(7, 11, original));
        assertThat(service.requestHash(7, 11, reversed))
                .isNotEqualTo(service.requestHash(7, 11, original));
    }

    private StudentAttendanceOfflineSyncRequest request(String remarks) {
        return new StudentAttendanceOfflineSyncRequest(
                3L, 3L, 3L, LocalDate.of(2026, 9, 11), 0L,
                StudentAttendanceOfflineSyncMode.PARTIAL,
                List.of(3L, 4L),
                List.of(
                        new StudentAttendanceOfflineSyncRecordRequest(
                                3L, 0L, AttendanceStatus.PRESENT, remarks
                        ),
                        new StudentAttendanceOfflineSyncRecordRequest(
                                4L, null, AttendanceStatus.ABSENT, null
                        )
                )
        );
    }
}
