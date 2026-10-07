package com.dawnrise.academic.results.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ResultSubjectMarkResponse(
        Long id,
        Long studentEnrollmentId,
        Long studentUserId,
        String studentDisplayName,
        String rollNumber,
        Boolean absent,
        BigDecimal marksObtained,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
