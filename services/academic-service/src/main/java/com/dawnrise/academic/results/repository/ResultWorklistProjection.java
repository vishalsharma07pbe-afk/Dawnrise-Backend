package com.dawnrise.academic.results.repository;

import com.dawnrise.academic.results.enums.ResultSubjectSheetStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ResultWorklistProjection(
        String taskType,
        Long academicYearId,
        String academicYearName,
        Long examinationId,
        String examinationName,
        Long scheduledAssessmentId,
        LocalDate assessmentDate,
        BigDecimal maximumMarks,
        Long gradeLevelId,
        String gradeLevelCode,
        String gradeLevelName,
        Long sectionId,
        String sectionCode,
        String sectionName,
        Long gradeLevelSubjectId,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Long subjectSheetId,
        ResultSubjectSheetStatus sheetStatus,
        Long sheetVersion,
        Long totalStudents,
        Long completedMarks
) {}
