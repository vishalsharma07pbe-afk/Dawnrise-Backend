package com.dawnrise.academic.reportcard.dto;

import java.math.BigDecimal;

public record ReportCardSubjectResponse(long subjectId, String subjectCode, String subjectName, BigDecimal maximumMarks,
                                        BigDecimal obtainedMarks, boolean absent, BigDecimal percentage,
                                        String gradeLabel, boolean passed, int displayOrder) {
}
