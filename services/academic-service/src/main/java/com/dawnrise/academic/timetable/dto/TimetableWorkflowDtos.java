package com.dawnrise.academic.timetable.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public final class TimetableWorkflowDtos {
    private TimetableWorkflowDtos() {}

    public record TransitionRequest(@Min(0) long expectedVersion) {}
    public record ReturnRequest(@Min(0) long expectedVersion,
                                @NotBlank @Size(max=1000) String reason) {}
    public record PublishRequest(@Min(0) long expectedVersion,
                                 LocalDate effectiveFrom,
                                 LocalDate effectiveToExclusive) {}
    public record SchoolCalendarDate(LocalDate date, String timeZoneId) {}
    public record WorkflowEvent(long id, TimetableDraftDtos.WorkflowStatus fromStatus,
                                TimetableDraftDtos.WorkflowStatus toStatus, String reason,
                                long actorUserId, OffsetDateTime occurredAt) {}
    public record WorkflowResponse(long draftId, long academicYearId,
                                   TimetableDraftDtos.WorkflowStatus status, long version,
                                   Long publicationId, List<WorkflowEvent> history) {}
    public record PublishedEntry(long gradeLevelId, String gradeName, long sectionId,
                                 String sectionName, long wingId, String wingName,
                                 long bellScheduleIntervalId, int dayOfWeek,
                                 LocalTime startTime, LocalTime endTime,
                                 TimetableConfigurationDtos.IntervalType intervalType,
                                 String activityName, Long gradeLevelSubjectId,
                                 String subjectName, Long teacherUserId, String teacherDisplayName, Long roomId,
                                 String roomName) {}
    public record PublishedTimetable(long publicationId, long academicYearId, String schoolName,
                                     String academicYearName, LocalDate effectiveFrom,
                                     LocalDate effectiveToExclusive, OffsetDateTime publishedAt,
                                     OffsetDateTime generatedAt, String timeZoneId,
                                     List<PublishedEntry> entries) {}
}
