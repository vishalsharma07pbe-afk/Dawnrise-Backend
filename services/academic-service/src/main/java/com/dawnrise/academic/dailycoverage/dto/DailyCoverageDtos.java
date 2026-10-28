package com.dawnrise.academic.dailycoverage.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public final class DailyCoverageDtos {
    private DailyCoverageDtos() {}

    public enum AbsenceType { KNOWN_LEAVE, UNEXPECTED_ABSENCE, LATE_ARRIVAL, EARLY_DEPARTURE }
    public enum ArrangementType { ORIGINAL, TEACHER_COVER, ALTERNATIVE_ACTIVITY }

    public record RecordAbsenceRequest(@Positive long teacherUserId, @NotNull LocalDate date,
            @NotNull AbsenceType type, LocalTime startTime, LocalTime endTime,
            List<@Positive Long> wingIds, @NotBlank @Size(max = 1000) String reason,
            @NotBlank @Size(max = 200) String requestKey) {}
    public record CorrectAbsenceRequest(@Min(0) long expectedVersion, AbsenceType type,
            LocalTime startTime, LocalTime endTime, @NotBlank @Size(max = 1000) String reason,
            @NotBlank @Size(max = 200) String requestKey) {}
    public record ReasonedVersionRequest(@Min(0) long expectedVersion,
            @NotBlank @Size(max = 1000) String reason,
            @NotBlank @Size(max = 200) String requestKey) {}
    public record VersionRequest(@Min(0) long expectedVersion,
            @NotBlank @Size(max = 200) String requestKey) {}
    public record CandidateChoiceRequest(@NotBlank @Size(max = 200) String candidateKey,
            @Min(0) long expectedVersion, @Size(max = 1000) String reason,
            @Size(max = 200) String requestKey) {}

    public record MutationResponse(long id, long version) {}
    public record CoverageReason(String code, String message) {}
    public record AssignmentView(Long teacherUserId, String teacherDisplayName,
            Long gradeLevelSubjectId, String subjectName, Long roomId, String roomName,
            String activityName, ArrangementType arrangementType) {}
    public record ProposalSummary(long id, long version, String mode, String status,
            String candidateKey, AssignmentView assignment, OffsetDateTime proposedAt) {}
    public record HistoryEvent(long id, String eventType, String summary, String reason,
            String actorDisplayName, OffsetDateTime occurredAt) {}
    public record AffectedPeriod(long id, long version, LocalDate date, long publicationId,
            long publishedSlotId, long bellScheduleIntervalId, LocalTime startTime, LocalTime endTime,
            long wingId, String wingName, long gradeLevelId, String gradeName,
            long sectionId, String sectionName, AssignmentView originalAssignment,
            String status, String urgency, OffsetDateTime deadlineAt, long absenceId,
            AbsenceType absenceType, String absenceReason, ProposalSummary proposal,
            List<HistoryEvent> history) {
        public AffectedPeriod withoutHistory() {
            return new AffectedPeriod(id, version, date, publicationId, publishedSlotId,
                    bellScheduleIntervalId, startTime, endTime, wingId, wingName, gradeLevelId,
                    gradeName, sectionId, sectionName, originalAssignment, status, urgency,
                    deadlineAt, absenceId, absenceType, absenceReason, proposal, List.of());
        }
    }
    public record Candidate(String candidateKey, String kind, int rank, boolean eligible,
            boolean recommended, AssignmentView assignment, List<CoverageReason> reasons,
            List<CoverageReason> exclusions, Integer travelMinutes, Integer workloadPeriods) {}
    public record ManualPreview(boolean valid, AssignmentView assignment,
            List<CoverageReason> reasons, List<CoverageReason> conflicts,
            long affectedPeriodVersion) {}
    public record CoverageAlert(long id, String severity, String message,
            long affectedPeriodId, OffsetDateTime deadlineAt) {}

    public record ApprovedCoverage(long caseId, long proposalId, String status,
            String mode, OffsetDateTime approvedAt, String reason) {}
    public record DailyEntry(long publishedSlotId, long gradeLevelId, String gradeName,
            long sectionId, String sectionName, long wingId, String wingName,
            long bellScheduleIntervalId, int dayOfWeek, LocalTime startTime, LocalTime endTime,
            String intervalType, String activityName, Long gradeLevelSubjectId, String subjectName,
            Long teacherUserId, String teacherDisplayName, Long roomId, String roomName,
            AssignmentView originalAssignment, AssignmentView actualAssignment,
            ApprovedCoverage coverage) {}
    public record DailyTimetable(long publicationId, long academicYearId, String schoolName,
            String academicYearName, LocalDate effectiveFrom, LocalDate effectiveToExclusive,
            OffsetDateTime publishedAt, OffsetDateTime generatedAt, String timeZoneId,
            List<DailyEntry> entries) {}
}
