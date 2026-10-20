package com.dawnrise.academic.reportcard.dto;

import com.dawnrise.academic.reportcard.enums.ReportCardStatus;

import java.math.*;
import java.time.OffsetDateTime;
import java.util.List;

public record ReportCardResponse(long id, long publicationId, long academicYearId, long examinationId,
                                 long gradeLevelId, long sectionId, long studentUserId, String studentDisplayName, long enrollmentId,
                                 ReportCardStatus status, String academicYearName, String examinationName,
                                 String gradeName, String sectionName, String rollNumber,
                                 OffsetDateTime resultPublishedAt, String policyName, int policyVersion,
                                 BigDecimal subjectPassPercentage, BigDecimal overallPassPercentage,
                                 boolean absentCountsAsFailure, boolean requireEverySubjectToPass, int decimalScale,
                                 RoundingMode roundingMode, List<ReportCardSubjectResponse> subjects,
                                 BigDecimal totalMaximumMarks, BigDecimal totalObtainedMarks,
                                 BigDecimal aggregatePercentage, String overallGrade, boolean passed,
                                 int absentSubjectCount, boolean partial, String returnReason, long version,
                                 OffsetDateTime publishedAt) {
}
