package com.dawnrise.academic.results.dto;

import java.math.BigDecimal;

public record PublishedResultSubjectResponse(
        long subjectId,
        String subjectCode,
        String subjectName,
        BigDecimal maximumMarks,
        BigDecimal marksObtained,
        boolean absent
) {
}
