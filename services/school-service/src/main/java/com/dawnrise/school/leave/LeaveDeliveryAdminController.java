package com.dawnrise.school.leave;

import com.dawnrise.school.leave.LeaveDtos.RequeueRequest;
import com.dawnrise.school.school.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff-leave/delivery-failures")
@PreAuthorize("@schoolTenantSecurity.hasOrganization(authentication) and hasAuthority('STAFF_LEAVE_ADMIN')")
public class LeaveDeliveryAdminController {
    private final LeaveCoverageOutboxStore store;private final RecentAuthenticationGuard recent;
    public LeaveDeliveryAdminController(LeaveCoverageOutboxStore store,RecentAuthenticationGuard recent){this.store=store;this.recent=recent;}
    @GetMapping public List<LeaveCoverageOutboxStore.DeadLetter> list(@AuthenticationPrincipal Jwt jwt){return store.deadLetters(org(jwt));}
    @PostMapping("/{id}/requeue") public void requeue(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody RequeueRequest request){recent.require(jwt);if(!store.requeue(org(jwt),id,user(jwt),request.reason()))throw new ResourceNotFoundException("Leave delivery failure not found");}
    private long org(Jwt jwt){return ((Number)jwt.getClaim("organizationId")).longValue();}private long user(Jwt jwt){return Long.parseLong(jwt.getSubject());}
}
