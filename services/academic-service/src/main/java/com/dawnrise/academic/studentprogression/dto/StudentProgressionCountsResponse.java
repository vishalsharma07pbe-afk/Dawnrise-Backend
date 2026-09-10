package com.dawnrise.academic.studentprogression.dto;

public record StudentProgressionCountsResponse(
        int total,
        int promoted,
        int repeated,
        int graduated,
        int left,
        int manualReview
) {
}