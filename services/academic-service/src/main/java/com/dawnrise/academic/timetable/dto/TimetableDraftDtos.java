package com.dawnrise.academic.timetable.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class TimetableDraftDtos {
    private TimetableDraftDtos() {}

    public enum ActivityType { TEACHING, SUPERVISED_ACTIVITY }
    public enum WorkflowStatus { DRAFT, IN_REVIEW, APPROVED, PUBLISHED }
    public enum DraftSource { MANUAL, GENERATED }
    public enum ConflictSeverity { ERROR, INCOMPLETE, WARNING }

    public record CreateDraftRequest(@Positive long academicYearId, @NotBlank @Size(max=120) String name) {}
    public record EffectiveDateIntentRequest(LocalDate effectiveFrom, LocalDate effectiveToExclusive,
                                             @Min(0) long expectedVersion) {}
    public record LessonRequest(@Positive long sectionId, @Positive long bellScheduleIntervalId,
                                @NotNull ActivityType activityType, @NotBlank @Size(max=120) String activityName,
                                Long gradeLevelSubjectId, Long teacherUserId, Long roomId,
                                @Min(0) long expectedDraftVersion) {}
    public record MoveLessonRequest(@Positive long sectionId, @Positive long bellScheduleIntervalId,
                                    Long teacherUserId, Long roomId,
                                    @Min(0) long expectedDraftVersion, @Min(0) long expectedSlotVersion) {}
    public record SwapLessonsRequest(@Positive long otherSlotId, @Min(0) long expectedDraftVersion,
                                     @Min(0) long expectedSlotVersion, @Min(0) long expectedOtherSlotVersion) {}
    public record VersionRequest(@Min(0) long expectedDraftVersion, @Min(0) Long expectedSlotVersion) {}
    public record ConflictPreviewRequest(Long slotId, @NotNull LessonRequest proposed) {}

    public record DraftResponse(long id, long academicYearId, String name, WorkflowStatus workflowStatus, long version,
                                long createdByUserId, long updatedByUserId, OffsetDateTime createdAt,
                                OffsetDateTime updatedAt, List<SlotResponse> slots) {}
    public record SlotResponse(long id, long sectionId, long gradeLevelId, long wingId,
                               long bellScheduleIntervalId, int dayOfWeek, LocalTime startTime, LocalTime endTime,
                               ActivityType activityType, String activityName, Long gradeLevelSubjectId,
                               Long teacherUserId, Long roomId, boolean pinned, long version) {}
    public record SectionDraftResponse(long draftId, long academicYearId, long sectionId, long draftVersion,
                                       List<TimetableConfigurationDtos.BellDayResponse> resolvedDays,
                                       List<SlotResponse> slots) {}
    public record Conflict(String code, ConflictSeverity severity, String message,
                           Long conflictingSlotId, Long teacherUserId, Long roomId,
                           Long sectionId, Long gradeLevelSubjectId) {}
    public record Suggestion(String type, Long id, String label, Integer dayOfWeek,
                             Long bellScheduleIntervalId) {}
    public record ConflictPreview(boolean canApply, List<Conflict> conflicts, List<Suggestion> suggestions) {}
    public record ValidationReport(boolean editValid, boolean complete, List<Conflict> issues) {}
    public record DraftSummary(long id, long academicYearId, String name, WorkflowStatus workflowStatus,
                               DraftSource source, Long generationJobId, Integer proposalIndex,
                               long version, OffsetDateTime updatedAt, LocalDate effectiveFromIntent,
                               LocalDate effectiveToExclusiveIntent) {}
    public record DraftPage(List<DraftSummary> content, int page, int size, long totalElements,
                            int totalPages) {}
    public record EditAuditEvent(long id, String action, Long slotId, long actorUserId,
                                 OffsetDateTime occurredAt) {}
    public record DraftHistory(List<EditAuditEvent> edits,
                               List<TimetableWorkflowDtos.WorkflowEvent> workflow) {}
}
