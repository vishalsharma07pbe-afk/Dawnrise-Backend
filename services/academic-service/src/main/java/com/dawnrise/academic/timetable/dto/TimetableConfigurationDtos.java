package com.dawnrise.academic.timetable.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public final class TimetableConfigurationDtos {
    private TimetableConfigurationDtos() {
    }

    public enum IntervalType { TEACHING, ASSEMBLY, SUPERVISED_ACTIVITY, RECESS, TRANSITION, FIXED_NON_STUDENT }
    public enum CoverageMode { NONE, SECTION, SECTION_GROUP, WHOLE_WING }
    public enum AssigneeRequirement { NONE, QUALIFIED_SUBJECT_TEACHER, TEACHER_OR_APPROVED_SUPERVISOR }
    public enum AvailabilityType { AVAILABLE, UNAVAILABLE, PREFERRED, DISCOURAGED }
    public enum EligibilityType { ALTERNATE, TEMPORARY }
    public enum AssignmentScope { WING_DEFAULT, GRADE_OVERRIDE }
    public enum FeasibilitySeverity { ERROR, WARNING }

    public record WingRequest(@NotBlank @Size(max = 50) String code,
                              @NotBlank @Size(max = 120) String name,
                              @NotNull Boolean active,
                              @Positive Integer displayOrder,
                              @Min(0) Long expectedVersion) {}
    public record WingResponse(long id, String code, String name, boolean active,
                               int displayOrder, long version) {}

    public record GradeWingRequest(@Positive long academicYearId, @Positive long gradeLevelId,
                                   @Positive long wingId, @Min(0) Long expectedVersion) {}
    public record GradeWingResponse(long id, long academicYearId, long gradeLevelId,
                                    long wingId, long version) {}

    public record BellScheduleRequest(@NotBlank @Size(max = 120) String name,
                                      @Size(max = 500) String description,
                                      @NotNull Boolean active,
                                      @Min(0) Long expectedVersion,
                                      @NotEmpty List<@Valid BellDayRequest> days) {}
    public record BellDayRequest(@Min(1) @Max(7) int dayOfWeek, boolean workingDay,
                                 LocalTime dayStartTime, LocalTime dayEndTime,
                                 List<@Valid BellIntervalRequest> intervals) {}
    public record BellIntervalRequest(@NotNull IntervalType intervalType,
                                      @NotBlank @Size(max = 120) String name,
                                      @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                                      @Positive int displayOrder, @NotNull CoverageMode coverageMode,
                                      @NotNull AssigneeRequirement assigneeRequirement,
                                      boolean schedulable) {}
    public record BellScheduleResponse(long id, String name, String description, boolean active,
                                       long version, List<BellDayResponse> days) {}
    public record BellDayResponse(long id, int dayOfWeek, boolean workingDay,
                                  LocalTime dayStartTime, LocalTime dayEndTime,
                                  List<BellIntervalResponse> intervals) {}
    public record BellIntervalResponse(long id, IntervalType intervalType, String name,
                                       LocalTime startTime, LocalTime endTime, int displayOrder,
                                       CoverageMode coverageMode, AssigneeRequirement assigneeRequirement,
                                       boolean schedulable) {}

    public record ScheduleAssignmentRequest(@Positive long academicYearId, @Positive long wingId,
                                            Long gradeLevelId, @Positive long bellScheduleId,
                                            @Min(0) Long expectedVersion) {}
    public record ScheduleAssignmentResponse(long id, long academicYearId, long wingId,
                                             Long gradeLevelId, long bellScheduleId, long version) {}

    public record SubjectRequirementRequest(@Positive long academicYearId, @Positive long gradeLevelId,
                                            @Positive long sectionId, @Positive long gradeLevelSubjectId,
                                            @Positive int periodsPerWeek, boolean requiredEachWorkingDay,
                                            @Positive int maximumPeriodsPerDay,
                                            @Min(0) int doublePeriodBlocksPerWeek,
                                            boolean allowConsecutivePeriods,
                                            @Min(0) Long expectedVersion) {}
    public record SubjectRequirementResponse(long id, long academicYearId, long gradeLevelId,
                                             long sectionId, long gradeLevelSubjectId,
                                             int periodsPerWeek, boolean requiredEachWorkingDay,
                                             int maximumPeriodsPerDay, int doublePeriodBlocksPerWeek,
                                             boolean allowConsecutivePeriods,
                                             long version) {}

    public record TeacherPolicyRequest(@Positive long academicYearId, @Positive long teacherUserId,
                                       @Positive int maximumPeriodsPerDay,
                                       @Positive int maximumPeriodsPerWeek,
                                       @Positive int maximumConsecutivePeriods,
                                       @Min(0) int minimumRestMinutes,
                                       @Min(0) Long expectedVersion) {}
    public record TeacherAvailabilityRequest(@Positive long academicYearId, @Positive long teacherUserId,
                                             @Min(1) @Max(7) int dayOfWeek,
                                             @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                                             @NotNull AvailabilityType availabilityType,
                                             @Size(max = 300) String reason) {}
    public record TeacherAvailabilityUpdateRequest(@Min(1) @Max(7) int dayOfWeek,
                                                   @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                                                   @NotNull AvailabilityType availabilityType,
                                                   @Size(max = 300) String reason,
                                                   @Min(0) long expectedVersion) {}
    public record VersionRequest(@Min(0) long expectedVersion) {}
    public record TeacherPolicyResponse(long id, long academicYearId, long teacherUserId,
                                        int maximumPeriodsPerDay, int maximumPeriodsPerWeek,
                                        int maximumConsecutivePeriods, int minimumRestMinutes, long version) {}
    public record TeacherAvailabilityResponse(long id, long academicYearId, long teacherUserId,
                                              int dayOfWeek, LocalTime startTime, LocalTime endTime,
                                              AvailabilityType availabilityType, String reason, long version) {}
    public record AlternateEligibilityRequest(@Positive long academicYearId,
                                              @Positive long teacherUserId,
                                              @Positive long gradeLevelSubjectId,
                                              @Positive long gradeLevelId, Long sectionId, Long wingId,
                                              @NotNull EligibilityType eligibilityType,
                                              LocalDate validFrom, LocalDate validTo,
                                              boolean active, @Min(0) Long expectedVersion) {}
    public record AlternateEligibilityResponse(long id, long academicYearId, long teacherUserId,
                                               long gradeLevelSubjectId, long gradeLevelId,
                                               Long sectionId, Long wingId, EligibilityType eligibilityType,
                                               LocalDate validFrom, LocalDate validTo, boolean active,
                                               long approvedByUserId, long version) {}

    public record RoomRequest(Long wingId, @NotBlank @Size(max = 50) String code,
                              @NotBlank @Size(max = 120) String name, @Positive Integer capacity,
                              boolean exclusiveUse, boolean active,
                              Set<@NotBlank @Size(max = 50) String> capabilities,
                              @Min(0) Long expectedVersion) {}
    public record RoomResponse(long id, Long wingId, String code, String name, Integer capacity,
                               boolean exclusiveUse, boolean active, Set<String> capabilities,
                               long version) {}
    public record SectionHomeRoomRequest(@Positive long academicYearId,
                                         @Positive long gradeLevelId,
                                         @Positive long sectionId,
                                         @Positive long roomId,
                                         @Min(0) Long expectedVersion) {}
    public record SectionHomeRoomResponse(long id, long academicYearId, long gradeLevelId,
                                          long sectionId, long roomId, long version) {}
    public record SubjectRoomRequirementRequest(@Positive long academicYearId,
                                                @Positive long gradeLevelId,
                                                @Positive long gradeLevelSubjectId,
                                                @NotBlank @Size(max = 50) String capabilityCode,
                                                boolean required, @Min(0) Long expectedVersion) {}
    public record SubjectRoomRequirementResponse(long id, long academicYearId, long gradeLevelId,
                                                 long gradeLevelSubjectId, String capabilityCode,
                                                 boolean required, long version) {}

    public record AcademicYearConfiguration(long academicYearId,
                                            List<GradeWingResponse> gradeWings,
                                            List<ScheduleAssignmentResponse> scheduleAssignments,
                                            List<SubjectRequirementResponse> subjectRequirements,
                                            List<TeacherPolicyResponse> teacherPolicies,
                                            List<TeacherAvailabilityResponse> teacherAvailability,
                                            List<AlternateEligibilityResponse> alternateTeacherEligibilities,
                                            List<SectionHomeRoomResponse> sectionHomeRooms,
                                            List<SubjectRoomRequirementResponse> subjectRoomRequirements) {}

    public record ResolvedSectionSchedule(long organizationId, long academicYearId,
                                          long gradeLevelId, long sectionId, long wingId,
                                          AssignmentScope assignmentScope, String timeZoneId,
                                          BellScheduleResponse bellSchedule) {}
    public record FeasibilityIssue(String code, FeasibilitySeverity severity, String message,
                                   Long sectionId, Long gradeLevelSubjectId, Long teacherUserId,
                                   String capabilityCode) {}
    public record FeasibilityReport(long organizationId, long academicYearId,
                                    OffsetDateTime evaluatedAt, boolean deterministicChecksPassed,
                                    boolean completeTimetableProven, List<FeasibilityIssue> issues) {}
    public record LabeledReference(long id, String label) {}
    public record TeacherReference(long id, String displayName) {}
    public record ConfigurationOptions(List<LabeledReference> wings, List<LabeledReference> grades,
                                       List<LabeledReference> sections, List<LabeledReference> subjects,
                                       List<LabeledReference> rooms, List<TeacherReference> teachers) {}
}
