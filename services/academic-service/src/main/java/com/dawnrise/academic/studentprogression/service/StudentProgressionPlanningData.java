package com.dawnrise.academic.studentprogression.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentenrollment.integration.identity.StudentEnrollmentEligibilityResponse;
import com.dawnrise.academic.studentprogression.entity.StudentProgressionItem;

import java.util.List;

public record StudentProgressionPlanningData(

        AcademicYear sourceAcademicYear,
        AcademicYear targetAcademicYear,

        List<StudentEnrollment> sourceEnrollments,
        List<StudentProgressionItem> existingProgressionItems,

        List<GradeLevel> sourceGradeLevels,
        List<Section> sourceSections,

        List<GradeLevel> targetGradeLevels,
        List<Section> targetSections,
        List<GradeLevelSubject> targetGradeLevelSubjects,

        List<StudentEnrollment> existingTargetEnrollments,
        List<StudentEnrollmentEligibilityResponse> eligibilityResponses
) {
    public StudentProgressionPlanningData {
        sourceEnrollments = immutable(sourceEnrollments);
        existingProgressionItems =
                immutable(existingProgressionItems);
        sourceGradeLevels = immutable(sourceGradeLevels);
        sourceSections = immutable(sourceSections);
        targetGradeLevels = immutable(targetGradeLevels);
        targetSections = immutable(targetSections);
        targetGradeLevelSubjects =
                immutable(targetGradeLevelSubjects);
        existingTargetEnrollments =
                immutable(existingTargetEnrollments);
        eligibilityResponses =
                immutable(eligibilityResponses);
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null
                ? List.of()
                : List.copyOf(values);
    }
}