package com.dawnrise.school.leave;

import com.dawnrise.school.leave.LeaveDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/staff-leave")
@PreAuthorize("@schoolTenantSecurity.hasOrganization(authentication)")
public class StaffLeaveController {
    private final StaffLeaveService service; private final RecentAuthenticationGuard recent;
    public StaffLeaveController(StaffLeaveService service,RecentAuthenticationGuard recent){this.service=service;this.recent=recent;}

    @GetMapping("/policies") @PreAuthorize("hasAuthority('STAFF_LEAVE_SELF')")
    public List<PolicyView> policies(@AuthenticationPrincipal Jwt jwt){return service.policies(org(jwt));}
    @PostMapping("/policies") @PreAuthorize("hasAuthority('STAFF_LEAVE_ADMIN')")
    public PolicyView createPolicy(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody PolicyInput input){recent.require(jwt);return service.createPolicy(org(jwt),user(jwt),input);}
    @PutMapping("/policies/{id}") @PreAuthorize("hasAuthority('STAFF_LEAVE_ADMIN')")
    public PolicyView updatePolicy(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody PolicyInput input){recent.require(jwt);return service.updatePolicy(org(jwt),user(jwt),id,input);}
    @GetMapping("/approval-rules") @PreAuthorize("hasAuthority('STAFF_LEAVE_ADMIN')")
    public List<ApprovalRuleView> approvalRules(@AuthenticationPrincipal Jwt jwt){return service.approvalRules(org(jwt));}
    @PostMapping("/approval-rules") @PreAuthorize("hasAuthority('STAFF_LEAVE_ADMIN')")
    public ApprovalRuleView createApprovalRule(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ApprovalRule input){recent.require(jwt);return service.createApprovalRule(org(jwt),input);}
    @PutMapping("/approval-rules/{id}") @PreAuthorize("hasAuthority('STAFF_LEAVE_ADMIN')")
    public ApprovalRuleView updateApprovalRule(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody ApprovalRule input){recent.require(jwt);return service.updateApprovalRule(org(jwt),id,input);}
    @GetMapping("/balances/self") @PreAuthorize("hasAuthority('STAFF_LEAVE_SELF')")
    public List<BalanceView> balances(@AuthenticationPrincipal Jwt jwt){return service.balances(org(jwt),user(jwt));}
    @GetMapping("/balances/{staffUserId}") @PreAuthorize("hasAuthority('STAFF_LEAVE_ADMIN')")
    public List<BalanceView> staffBalances(@AuthenticationPrincipal Jwt jwt,@PathVariable long staffUserId){return service.balances(org(jwt),staffUserId);}
    @PostMapping("/balances/{balanceId}/adjustments") @PreAuthorize("hasAuthority('STAFF_LEAVE_ADMIN')")
    public BalanceView adjust(@AuthenticationPrincipal Jwt jwt,@PathVariable long balanceId,@Valid @RequestBody BalanceAdjustment input){recent.require(jwt);return service.adjust(org(jwt),user(jwt),balanceId,input);}
    @PostMapping("/requests/preview") @PreAuthorize("hasAuthority('STAFF_LEAVE_SELF')")
    public Preview preview(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody RequestInput input){return service.preview(org(jwt),user(jwt),input);}
    @PostMapping("/requests") @PreAuthorize("hasAuthority('STAFF_LEAVE_SELF')")
    public RequestView draft(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody RequestInput input){return service.createDraft(org(jwt),user(jwt),input);}
    @PutMapping("/requests/{id}") @PreAuthorize("hasAuthority('STAFF_LEAVE_SELF')")
    public RequestView updateDraft(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@RequestParam long expectedVersion,@Valid @RequestBody RequestInput input){return service.updateDraft(org(jwt),user(jwt),id,expectedVersion,input);}
    @GetMapping("/requests/self") @PreAuthorize("hasAuthority('STAFF_LEAVE_SELF')")
    public List<RequestView> mine(@AuthenticationPrincipal Jwt jwt){return service.mine(org(jwt),user(jwt));}
    @PostMapping("/requests/{id}/submit") @PreAuthorize("hasAuthority('STAFF_LEAVE_SELF')")
    public RequestView submit(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody VersionRequest input){return service.submit(org(jwt),user(jwt),id,input.expectedVersion());}
    @PostMapping("/requests/{id}/withdraw") @PreAuthorize("hasAuthority('STAFF_LEAVE_SELF')")
    public RequestView withdraw(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody VersionRequest input){return service.withdraw(org(jwt),user(jwt),id,input.expectedVersion());}
    @PostMapping("/requests/{id}/cancel") @PreAuthorize("hasAuthority('STAFF_LEAVE_SELF')")
    public RequestView cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody DecisionRequest input){recent.require(jwt);return service.cancel(org(jwt),user(jwt),id,input);}
    @PostMapping("/requests/{id}/partial-cancel") @PreAuthorize("hasAuthority('STAFF_LEAVE_SELF')")
    public RequestView partialCancel(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody PartialCancelRequest input){recent.require(jwt);return service.partialCancel(org(jwt),user(jwt),id,input);}
    @GetMapping("/approvals") @PreAuthorize("hasAuthority('STAFF_LEAVE_APPROVE')")
    public List<RequestView> queue(@AuthenticationPrincipal Jwt jwt){return service.queue(org(jwt),user(jwt));}
    @PostMapping("/approvals/{id}/approve") @PreAuthorize("hasAuthority('STAFF_LEAVE_APPROVE')")
    public RequestView approve(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody DecisionRequest input){recent.require(jwt);return service.approve(org(jwt),user(jwt),id,input);}
    @PostMapping("/approvals/{id}/reject") @PreAuthorize("hasAuthority('STAFF_LEAVE_APPROVE')")
    public RequestView reject(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody DecisionRequest input){recent.require(jwt);return service.reject(org(jwt),user(jwt),id,input);}
    @GetMapping("/requests/{id}/audit") @PreAuthorize("hasAnyAuthority('STAFF_LEAVE_SELF','STAFF_LEAVE_APPROVE')")
    public List<AuditView> audit(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){List<String> permissions=jwt.getClaimAsStringList("permissions");return service.audit(org(jwt),user(jwt),id,permissions!=null&&permissions.contains("STAFF_LEAVE_APPROVE"));}
    @GetMapping("/calendar") @PreAuthorize("hasAuthority('STAFF_LEAVE_CALENDAR_VIEW')")
    public List<CalendarEntry> calendar(@AuthenticationPrincipal Jwt jwt,@RequestParam LocalDate start,@RequestParam LocalDate end){return service.calendar(org(jwt),start,end);}
    private long org(Jwt jwt){return ((Number)jwt.getClaim("organizationId")).longValue();}
    private long user(Jwt jwt){return Long.parseLong(jwt.getSubject());}
}
