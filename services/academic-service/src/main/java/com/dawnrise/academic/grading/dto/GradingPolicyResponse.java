package com.dawnrise.academic.grading.dto;

import com.dawnrise.academic.grading.enums.GradingPolicyStatus;

import java.math.*;
import java.time.OffsetDateTime;
import java.util.List;

public record GradingPolicyResponse(long id, long academicYearId, long gradeLevelId, String name, String notes,
                                    GradingPolicyStatus status, BigDecimal subjectPassPercentage,
                                    BigDecimal overallPassPercentage, boolean absentCountsAsFailure,
                                    boolean requireEverySubjectToPass, int decimalScale, RoundingMode roundingMode,
                                    int policyVersion, long version, List<GradeBandRequest> bands,
                                    OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
