package com.dawnrise.academic.results.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ResultWorklistTaskResponse(
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
        String status,
        Long sheetVersion,
        Long totalStudents,
        Long completedMarks
) {}
