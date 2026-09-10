package com.dawnrise.academic.studentprogression.mapper;

import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionCountsResponse;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionItemPreviewResponse;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionPreviewResponse;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlan;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlanItem;
import org.springframework.stereotype.Component;

@Component
public class StudentProgressionPreviewMapper {

    public StudentProgressionPreviewResponse toResponse(
            StudentProgressionPlan plan
    ) {
        return new StudentProgressionPreviewResponse(
                plan.sourceAcademicYear().getId(),
                plan.targetAcademicYear().getId(),
                plan.batchLabel(),
                new StudentProgressionCountsResponse(
                        plan.totalItems(),
                        Math.toIntExact(plan.promotedCount()),
                        Math.toIntExact(plan.repeatedCount()),
                        Math.toIntExact(plan.graduatedCount()),
                        Math.toIntExact(plan.leftCount()),
                        Math.toIntExact(plan.manualReviewCount())
                ),
                plan.canConfirm(),
                plan.previewFingerprint(),
                plan.items()
                        .stream()
                        .map(this::toItemResponse)
                        .toList(),
                plan.conflicts()
        );
    }

    private StudentProgressionItemPreviewResponse toItemResponse(
            StudentProgressionPlanItem item
    ) {
        StudentEnrollment source = item.sourceEnrollment();

        return new StudentProgressionItemPreviewResponse(
                item.decision().sourceEnrollmentId(),
                source == null
                        ? null
                        : source.getStudentUserId(),
                source == null
                        ? null
                        : source.getGradeLevelId(),
                source == null
                        ? null
                        : source.getSectionId(),
                source == null
                        ? null
                        : source.getRollNumber(),
                source == null
                        ? null
                        : source.getStatus(),
                item.decision().outcome(),
                item.decision().targetGradeLevelId(),
                item.decision().targetSectionId(),
                item.decision().targetRollNumber(),
                item.suggestedTargetGradeLevelId(),
                item.suggestedTargetSectionId(),
                item.decision().effectiveOn(),
                item.canConfirm(),
                item.conflicts()
        );
    }
}