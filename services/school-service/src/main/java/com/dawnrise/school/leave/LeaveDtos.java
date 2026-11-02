package com.dawnrise.school.leave;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public final class LeaveDtos {
    private LeaveDtos() {}

    public enum RequestKind { FULL_DAY, DATE_RANGE, HALF_DAY, LATE_ARRIVAL, EARLY_DEPARTURE, PARTIAL_TIME }
    public enum HalfDayPeriod { MORNING, AFTERNOON }

    public record PolicyInput(
            @Pattern(regexp="[A-Z][A-Z0-9_]{1,39}") String code,
            @NotBlank @Size(max=100) String name,
            @NotNull LocalDate activeFrom, LocalDate activeTo,
            @NotNull @DecimalMin("0") BigDecimal yearlyAllowance,
            boolean carryForwardEnabled, @DecimalMin("0") BigDecimal carryForwardLimit,
            @DecimalMin(value="0", inclusive=false) BigDecimal maximumConsecutiveDays,
            boolean supportingDocumentRequired, boolean paid, boolean partialTimeEnabled,
            boolean negativeBalanceAllowed, @NotEmpty Set<@NotBlank String> applicableRoles,
            @Min(0) Long expectedVersion) {}

    public record PolicyView(long id, String code, String name, LocalDate activeFrom, LocalDate activeTo,
            BigDecimal yearlyAllowance, boolean carryForwardEnabled, BigDecimal carryForwardLimit,
            BigDecimal maximumConsecutiveDays, boolean supportingDocumentRequired, boolean paid,
            boolean partialTimeEnabled, boolean negativeBalanceAllowed, Set<String> applicableRoles, long version) {}

    public record BalanceView(long id, long staffUserId, long leaveTypeId, int leaveYear,
            BigDecimal opening, BigDecimal accrued, BigDecimal carried, BigDecimal used,
            BigDecimal pending, BigDecimal adjusted, BigDecimal available, long version) {}

    public record BalanceAdjustment(@NotNull BigDecimal amount, @NotBlank @Size(max=1000) String reason,
                                    @Min(0) long expectedVersion) {}

    public record RequestInput(@Positive long leaveTypeId, @NotNull RequestKind kind,
            @NotNull LocalDate startDate, @NotNull LocalDate endDate, LocalTime startTime, LocalTime endTime,
            HalfDayPeriod halfDayPeriod, @NotBlank @Size(max=2000) String reason,
            @Size(max=200) String documentName, @Size(max=100) String documentContentType,
            @Size(max=500) String documentReference) {}

    public record RequestView(long id, long requesterUserId, long leaveTypeId, String leaveTypeName,
            RequestKind kind, LocalDate startDate, LocalDate endDate, LocalTime startTime, LocalTime endTime,
            HalfDayPeriod halfDayPeriod, BigDecimal units, String reason, String documentName,
            String status, int currentApprovalStep, long version, OffsetDateTime createdAt,
            List<ApprovalView> approvals) {}

    public record Preview(BigDecimal workingDays, BigDecimal projectedAvailable, boolean sufficient,
                          List<LocalDate> excludedDates) {}
    public record VersionRequest(@Min(0) long expectedVersion) {}
    public record DecisionRequest(@Min(0) long expectedVersion, @NotBlank @Size(max=1000) String reason) {}
    public record PartialCancelRequest(@Min(0) long expectedVersion, @NotNull LocalDate cancelFrom,
                                       @NotBlank @Size(max=1000) String reason) {}
    public record ApprovalView(int stepNumber, String approverRole, Long approverUserId, String status,
                               String reason, OffsetDateTime decidedAt, long version) {}
    public record AuditView(String action, long actorUserId, String reason, OffsetDateTime occurredAt) {}
    public record CalendarEntry(long requestId, long staffUserId, String displayName, LocalDate startDate,
                                LocalDate endDate, RequestKind kind, String status) {}
    public record ApprovalRule(@NotBlank String requesterRole, @Positive int stepNumber,
                               @NotBlank String approverRole, boolean active, @Min(0) Long expectedVersion) {}
    public record ApprovalRuleView(long id, String requesterRole, int stepNumber,
                                   String approverRole, boolean active, long version) {}
    public record RequeueRequest(@NotBlank @Size(max=1000) String reason) {}
}
