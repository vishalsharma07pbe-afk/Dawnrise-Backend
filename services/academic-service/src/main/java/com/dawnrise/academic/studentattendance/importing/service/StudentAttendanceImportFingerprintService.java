package com.dawnrise.academic.studentattendance.importing.service;

import com.dawnrise.academic.studentattendance.importing.dto.StudentAttendanceImportRowResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
public class StudentAttendanceImportFingerprintService {

    public String fingerprint(
            long organizationId,
            long actorUserId,
            String fileName,
            List<StudentAttendanceImportRowResponse> rows
    ) {
        StringBuilder canonical = new StringBuilder("attendance-import-v1|");
        append(canonical, organizationId);
        append(canonical, actorUserId);
        append(canonical, fileName);
        for (StudentAttendanceImportRowResponse row : rows) {
            append(canonical, row.rowNumber());
            append(canonical, row.academicYear());
            append(canonical, row.gradeCode());
            append(canonical, row.sectionCode());
            append(canonical, row.attendanceDate());
            append(canonical, row.rollNumber());
            append(canonical, row.studentEnrollmentId());
            append(canonical, row.expectedRecordVersion());
            append(canonical, row.attendanceStatus());
            append(canonical, row.remarks());
            row.errors().forEach(error -> append(canonical, error));
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    canonical.toString().getBytes(StandardCharsets.UTF_8)
            );
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void append(StringBuilder target, Object value) {
        String text = value == null ? "<null>" : value.toString();
        target.append(text.length()).append(':').append(text).append('|');
    }
}
