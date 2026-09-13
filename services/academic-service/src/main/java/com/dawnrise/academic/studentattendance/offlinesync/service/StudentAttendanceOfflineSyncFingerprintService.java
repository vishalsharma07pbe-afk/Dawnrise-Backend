package com.dawnrise.academic.studentattendance.offlinesync.service;

import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncRecordRequest;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class StudentAttendanceOfflineSyncFingerprintService {

    private static final int HASH_SCHEMA_VERSION = 1;

    public String requestHash(
            long organizationId,
            long actorUserId,
            StudentAttendanceOfflineSyncRequest request
    ) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, HASH_SCHEMA_VERSION);
        append(canonical, organizationId);
        append(canonical, actorUserId);
        append(canonical, request.academicYearId());
        append(canonical, request.gradeLevelId());
        append(canonical, request.sectionId());
        append(canonical, request.attendanceDate());
        append(canonical, request.baseSessionVersion());
        append(canonical, request.syncMode());
        if (request.expectedRosterEnrollmentIds() == null) {
            append(canonical, null);
        } else {
            append(canonical, request.expectedRosterEnrollmentIds().size());
            request.expectedRosterEnrollmentIds().stream()
                    .sorted()
                    .forEach(id -> append(canonical, id));
        }
        append(canonical, request.records() == null ? null : request.records().size());
        if (request.records() != null) {
            for (StudentAttendanceOfflineSyncRecordRequest record : request.records()) {
                if (record == null) {
                    append(canonical, null);
                    continue;
                }
                append(canonical, record.studentEnrollmentId());
                append(canonical, record.expectedRecordVersion());
                append(canonical, record.recordedStatus());
                append(canonical, normalize(record.remarks()));
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void append(StringBuilder target, Object value) {
        String text = value == null ? "<null>" : value.toString();
        target.append(text.length()).append(':').append(text).append('|');
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
