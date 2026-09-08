package com.dawnrise.academic.academicyearrollover.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyearrollover.dto.*;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.subject.entity.Subject;

import java.util.List;

public record RolloverPlan(
        RolloverOptions options,
        AcademicYear sourceAcademicYear,
        AcademicYear targetAcademicYear,
        List<GradeLevel> sourceGradeLevels,
        List<Section> sourceSections,
        List<Subject> sourceSubjects,
        List<GradeLevelSubject> sourceGradeLevelSubjects,
        List<GradeLevel> targetGradeLevels,
        List<Section> targetSections,
        List<Subject> targetSubjects,
        List<GradeLevelSubject> targetGradeLevelSubjects,
        List<RolloverConflictResponse> conflicts,
        String requestHash,
        String previewFingerprint
) {
    public RolloverCountsResponse counts() {
        return new RolloverCountsResponse(
                sourceGradeLevels.size(),
                sourceSections.size(),
                sourceSubjects.size(),
                sourceGradeLevelSubjects.size(),
                sourceGradeLevels.size()
                        + sourceSections.size()
                        + sourceSubjects.size()
                        + sourceGradeLevelSubjects.size()
        );
    }

    public AcademicYearStructureRolloverPreviewResponse toPreviewResponse() {
        return new AcademicYearStructureRolloverPreviewResponse(
                options.sourceAcademicYearId(),
                options.targetAcademicYearId(),
                conflicts.isEmpty(),
                requestHash,
                previewFingerprint,
                counts(),
                new RolloverProposedRecordsResponse(
                        sourceGradeLevels.stream()
                                .map(grade -> new ProposedGradeLevelResponse(
                                        grade.getId(),
                                        grade.getCode(),
                                        grade.getName(),
                                        grade.getDisplayOrder()
                                ))
                                .toList(),
                        sourceSections.stream()
                                .map(section -> new ProposedSectionResponse(
                                        section.getId(),
                                        section.getGradeLevelId(),
                                        section.getCode(),
                                        section.getName(),
                                        section.getDisplayOrder()
                                ))
                                .toList(),
                        sourceSubjects.stream()
                                .map(subject -> new ProposedSubjectResponse(
                                        subject.getId(),
                                        subject.getCode(),
                                        subject.getName(),
                                        subject.getDescription()
                                ))
                                .toList(),
                        sourceGradeLevelSubjects.stream()
                                .map(assignment ->
                                        new ProposedGradeLevelSubjectResponse(
                                                assignment.getId(),
                                                assignment.getGradeLevelId(),
                                                assignment.getSubjectId(),
                                                assignment.getMandatory(),
                                                assignment.getDisplayOrder()
                                        ))
                                .toList()
                ),
                conflicts
        );
    }
}
