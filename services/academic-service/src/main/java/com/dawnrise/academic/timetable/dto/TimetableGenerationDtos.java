package com.dawnrise.academic.timetable.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class TimetableGenerationDtos {
    private TimetableGenerationDtos() {}
    public enum JobStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCEL_REQUESTED, CANCELLED }
    public record GenerateRequest(@Positive long academicYearId,
                                  @Min(1) @Max(5) int proposalCount,
                                  long seed,
                                  @Min(1) @Max(300) int solveTimeSeconds,
                                  Long sourceDraftId,
                                  boolean preferClassTeacherFirst,
                                  @NotBlank @Size(max=80) String requestKey) {}
    public record CancelRequest(@Min(0) long expectedVersion) {}
    public record RetryRequest(@Min(0) long expectedVersion,
                               @NotBlank @Size(max=80) String requestKey) {}
    public record ProposalResponse(int proposalIndex, Long draftId, boolean feasible,
                                   long hardScore, long softScore, Map<String,Long> scoreBreakdown,
                                   List<TimetableDraftDtos.Conflict> validationFindings,
                                   List<String> unmetRequirements) {}
    public record JobResponse(long id,long academicYearId,String requestKey,JobStatus status,
                              int proposalCount,int completedProposals,long seed,int solveTimeSeconds,
                              Long sourceDraftId,boolean preferClassTeacherFirst,boolean cancelRequested,
                              String failureMessage,OffsetDateTime createdAt,OffsetDateTime startedAt,
                              OffsetDateTime finishedAt,long version,List<ProposalResponse> proposals) {}
}
