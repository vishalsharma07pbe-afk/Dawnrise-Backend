package com.dawnrise.academic.studentprogression.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionConflictResponse;
import com.dawnrise.academic.studentprogression.enums.StudentProgressionOutcome;

import java.util.List;

public record StudentProgressionPlan(

        AcademicYear sourceAcademicYear,
        AcademicYear targetAcademicYear,
        String batchLabel,

        List<StudentProgressionPlanItem> items,
        List<StudentProgressionConflictResponse> conflicts,

        String previewFingerprint
) {
    public StudentProgressionPlan {
        items = items == null
                ? List.of()
                : List.copyOf(items);

        conflicts = conflicts == null
                ? List.of()
                : List.copyOf(conflicts);
    }

    public int totalItems() {
        return items.size();
    }

    public long promotedCount() {
        return count(StudentProgressionOutcome.PROMOTED);
    }

    public long repeatedCount() {
        return count(StudentProgressionOutcome.REPEATED);
    }

    public long graduatedCount() {
        return count(StudentProgressionOutcome.GRADUATED);
    }

    public long leftCount() {
        return count(StudentProgressionOutcome.LEFT);
    }

    public long manualReviewCount() {
        return count(StudentProgressionOutcome.MANUAL_REVIEW);
    }

    public boolean canConfirm() {
        return conflicts.isEmpty()
                && !items.isEmpty()
                && items.stream()
                .allMatch(StudentProgressionPlanItem::canConfirm);
    }

    private long count(StudentProgressionOutcome outcome) {
        return items.stream()
                .filter(item ->
                        item.decision().outcome() == outcome
                )
                .count();
    }
}