package com.dawnrise.identity.studentguardian.controller;

import com.dawnrise.identity.studentguardian.dto.ActiveGuardianRecipientsResponse;
import com.dawnrise.identity.studentguardian.service.ActiveGuardianRecipientService;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/internal/v1/organizations/{organizationId}/students/{studentUserId}/guardians")
public class InternalStudentGuardianRecipientController {
    private final ActiveGuardianRecipientService recipients;

    public InternalStudentGuardianRecipientController(ActiveGuardianRecipientService recipients) {
        this.recipients = recipients;
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('INTERNAL_SERVICE') and authentication.name == 'academic-service'")
    public ActiveGuardianRecipientsResponse active(
            @PathVariable @Positive long organizationId,
            @PathVariable @Positive long studentUserId) {
        return new ActiveGuardianRecipientsResponse(recipients.activeGuardianUserIds(
                organizationId, studentUserId));
    }
}
