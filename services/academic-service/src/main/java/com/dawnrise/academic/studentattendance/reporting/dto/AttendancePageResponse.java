package com.dawnrise.academic.studentattendance.reporting.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Objects;

public record AttendancePageResponse<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        int numberOfElements,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty
) {

    public AttendancePageResponse {
        content = List.copyOf(
                Objects.requireNonNull(
                        content,
                        "Attendance page content is required"
                )
        );
        if (pageNumber < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative"
            );
        }
        if (pageSize < 0) {
            throw new IllegalArgumentException(
                    "Page size cannot be negative"
            );
        }
        if (numberOfElements < 0) {
            throw new IllegalArgumentException(
                    "Number of elements cannot be negative"
            );
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException(
                    "Total elements cannot be negative"
            );
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException(
                    "Total pages cannot be negative"
            );
        }
    }

    public static <T> AttendancePageResponse<T> from(Page<T> page) {
        Objects.requireNonNull(page, "Attendance page is required");
        return new AttendancePageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getNumberOfElements(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty()
        );
    }
}
