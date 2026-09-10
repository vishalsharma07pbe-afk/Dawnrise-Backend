package com.dawnrise.academic.studentprogression.service;

import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionConflictResponse;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionDecisionRequest;

import java.util.List;

public record StudentProgressionPlanItem(

        StudentProgressionDecisionRequest decision,

        StudentEnrollment sourceEnrollment,
        GradeLevel sourceGradeLevel,
        Section sourceSection,

        GradeLevel targetGradeLevel,
        Section targetSection,

        Long suggestedTargetGradeLevelId,
        Long suggestedTargetSectionId,

        List<StudentProgressionConflictResponse> conflicts
) {
    public StudentProgressionPlanItem {
        conflicts = conflicts == null
                ? List.of()
                : List.copyOf(conflicts);
    }

    public boolean canConfirm() {
        return conflicts.isEmpty();
    }
}