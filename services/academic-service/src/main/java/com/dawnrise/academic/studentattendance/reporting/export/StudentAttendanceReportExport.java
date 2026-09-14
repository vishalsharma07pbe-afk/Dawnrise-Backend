package com.dawnrise.academic.studentattendance.reporting.export;

import java.util.Objects;

public record StudentAttendanceReportExport(
        String filename,
        String contentType,
        byte[] content
) {

    private static final String SAFE_FILENAME_PATTERN =
            "[A-Za-z0-9._-]+";

    public StudentAttendanceReportExport {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException(
                    "Export filename is required"
            );
        }

        if (filename.contains("/")
                || filename.contains("\\")
                || filename.contains("\r")
                || filename.contains("\n")) {
            throw new IllegalArgumentException(
                    "Export filename is invalid"
            );
        }

        filename = filename.trim();

        if (!filename.matches(SAFE_FILENAME_PATTERN)) {
            throw new IllegalArgumentException(
                    "Export filename is invalid"
            );
        }

        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException(
                    "Export content type is required"
            );
        }

        Objects.requireNonNull(
                content,
                "Export content is required"
        );

        if (content.length == 0) {
            throw new IllegalArgumentException(
                    "Export content is required"
            );
        }

        contentType = contentType.trim();
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
