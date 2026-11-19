package com.dawnrise.academic.learning;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class LearningDtos {
    private LearningDtos() {}
    public record Scope(long academicYearId,long gradeLevelId,long sectionId,long gradeLevelSubjectId,String sectionName,String subjectName) {}
    public record Task(UUID id,@NotBlank @Size(max=2000) String description,@Size(max=30) String referenceType,@Size(max=1000) String referenceValue,boolean completed) {}
    public record Link(UUID id,UUID taskId,@NotBlank @Size(max=200) String title,@NotBlank @Pattern(regexp="https://.*") String url,@Pattern(regexp="REQUIRED|EXTRA_HELP") String kind,@PositiveOrZero Integer videoStartSeconds) {}
    public record Save(@NotBlank @Pattern(regexp="CLASSWORK|HOMEWORK") String type,@Positive long academicYearId,@Positive long gradeLevelId,@Positive long sectionId,@Positive long gradeLevelSubjectId,@NotBlank @Size(max=200) String title,@Size(max=10000) String instructions,LocalDate lessonDate,@Size(max=4000) String topics,@Size(max=2000) String pagesExercises,@Size(max=4000) String recap,OffsetDateTime dueAt,@Positive Long relatedClassworkId,@NotNull List<@Valid Task> tasks,@NotNull List<@Valid Link> links) {}
    public record Attachment(UUID id,UUID taskId,String name,String contentType,long size,String sha256,OffsetDateTime createdAt) {}
    public record Review(long studentUserId,String status,OffsetDateTime submittedAt,Long reviewedBy,OffsetDateTime reviewedAt,String feedback,long version) {}
    public record Post(long id,String type,String status,long academicYearId,long gradeLevelId,long sectionId,long gradeLevelSubjectId,long teacherUserId,String title,String instructions,LocalDate lessonDate,String topics,String pagesExercises,String recap,OffsetDateTime dueAt,Long relatedClassworkId,int points,long version,OffsetDateTime publishedAt,OffsetDateTime updatedAt,List<Task> tasks,List<Link> links,List<Attachment> attachments,Review review) {}
    public record ReviewDecision(@NotBlank @Pattern(regexp="VERIFIED|CHANGES_REQUESTED") String decision,@Size(max=2000) String feedback,long version) {}
    public record ReviewQueueItem(long postId,String title,OffsetDateTime dueAt,long studentUserId,OffsetDateTime submittedAt,long reviewVersion) {}
    public record PointEntry(long id,long academicYearId,long recipientUserId,String sourceType,String sourceId,int amount,long issuerUserId,String reason,Long adjustmentOfId,OffsetDateTime createdAt) {}
    public record PointsSummary(int total,List<PointEntry> entries) {}
    public record RewardSettings(long academicYearId,int verifiedHomeworkPoints,long version,Long updatedByUserId,OffsetDateTime updatedAt) {}
    public record SaveRewardSettings(@Positive long academicYearId,@PositiveOrZero @Max(10000) int verifiedHomeworkPoints,long version) {}
    public record PointCorrection(@NotNull Long awardId,@Min(-10000) @Max(10000) int amount,@NotBlank @Size(max=500) String reason) {}
    public record LeadershipProgress(long postId,String title,long sectionId,long assignedStudents,long inProgress,long readyForReview,long verified,long changesRequested) {}
    public record StudentHomeworkProgress(long studentUserId,String displayName,String status,int completedTasks,int totalTasks) {}
    public record TeacherHomeworkSummary(long postId,String title,String status,OffsetDateTime dueAt,long sectionId,String sectionName,String subjectName,int totalStudents,int completedStudents,int pendingStudents,int readyForReview,int verified,int changesRequested,List<StudentHomeworkProgress> students) {}
    public record ReminderRequest(@NotEmpty @Size(max=500) Set<@Positive Long> studentUserIds) {}
    public record ReminderResult(int studentCount,int recipientCount) {}
}
