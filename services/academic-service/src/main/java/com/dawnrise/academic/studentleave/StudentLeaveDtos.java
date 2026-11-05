package com.dawnrise.academic.studentleave;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class StudentLeaveDtos {
 private StudentLeaveDtos(){}
 public enum InitiatorType{STUDENT,GUARDIAN} public enum PartialDay{MORNING,AFTERNOON}
 public enum Decision{APPROVE,REJECT,RETURN}
 public record RequestInput(@NotNull LocalDate startDate,@NotNull LocalDate endDate,PartialDay partialDay,@NotBlank @Size(max=2000)String reason){}
 public record Preview(double workingDays,List<LocalDate>excludedDates,List<String>route){}
 public record DecisionInput(@Min(0)long expectedVersion,@Positive int revision,@NotNull Decision decision,@NotBlank @Size(max=1000)String comment,UUID decisionKey){}
 public record VersionInput(@Min(0)long expectedVersion){}
 public record CancelInput(@Min(0)long expectedVersion,@NotBlank @Size(max=1000)String comment){}
 public record Approval(int step,String stage,Long approverUserId,String status,String comment,OffsetDateTime decidedAt){}
 public record View(long id,long studentUserId,long initiatorUserId,String initiatorType,long academicYearId,long gradeLevelId,long sectionId,String status,int revision,String stage,double workingDays,long version,LocalDate startDate,LocalDate endDate,String partialDay,String reason,Long returnedBy,String returnComment,List<Approval>approvals,OffsetDateTime createdAt){}
 public record Attachment(UUID id,int revision,String name,String contentType,long size,String sha256,long uploadedByUserId,OffsetDateTime createdAt){}
 public record Audit(int revision,long actorUserId,String action,String detail,OffsetDateTime occurredAt){}
 public record PolicyInput(@Min(1) @Max(365) int longLeaveThresholdDays,boolean partialDayEnabled,@Min(0)long expectedVersion){}
 public record PolicyView(long id,int longLeaveThresholdDays,boolean partialDayEnabled,boolean active,long version){}
 public record ApproverMappingInput(@NotBlank @Pattern(regexp="ACADEMIC_HEAD|PRINCIPAL")String stage,Long academicYearId,Long gradeLevelId,Long sectionId,@Positive long approverUserId,boolean active,@Min(0)Long expectedVersion){}
 public record ApproverMappingView(long id,String stage,Long academicYearId,Long gradeLevelId,Long sectionId,long approverUserId,boolean active,long version){}
 public enum ReconciliationDecision{KEEP_ATTENDANCE,APPLY_LEAVE}
 public record ReconciliationInput(@NotNull ReconciliationDecision decision,@NotBlank @Size(max=1000)String comment){}
 public record AttendanceConflict(long projectionId,long requestId,int revision,long studentUserId,LocalDate attendanceDate,String projectedStatus,String conflictStatus,String registerStatus,String recordedStatus){}
}
