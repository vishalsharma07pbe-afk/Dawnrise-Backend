package com.dawnrise.identity.securityaudit.controller;

import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.securityaudit.dto.SecurityAuditEventResponse;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/security-audit-events")
@PreAuthorize(
        "@tenantSecurity.canAccessOrganization(authentication, #organizationId)"
)
public class SecurityAuditController {

    private final SecurityAuditService auditService;

    public SecurityAuditController(SecurityAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("""
        @tenantSecurity.canAccessOrganization(authentication, #organizationId)
        and hasAuthority('SECURITY_AUDIT_VIEW')
        """)
    public ResponseEntity<PageResponse<SecurityAuditEventResponse>> getEvents(
            @PathVariable Long organizationId,
            @PageableDefault(
                    size = 20,
                    sort = "occurredAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                auditService.getEvents(organizationId, pageable)
        );
    }
}
