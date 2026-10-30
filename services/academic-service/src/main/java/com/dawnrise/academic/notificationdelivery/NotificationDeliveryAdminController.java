package com.dawnrise.academic.notificationdelivery;

import com.dawnrise.academic.notificationdelivery.AcademicNotificationOutboxStore.DeadLetter;
import com.dawnrise.academic.notificationdelivery.AcademicNotificationOutboxStore.Source;
import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/notification-delivery/dead-letters")
@PreAuthorize("@academicTenantSecurity.hasOrganization(authentication) and "
        + "hasAnyAuthority('DAILY_COVERAGE_AUDIT_VIEW','STUDENT_ATTENDANCE_VIEW','RESULT_PUBLISH')")
public class NotificationDeliveryAdminController {
    private final AcademicNotificationOutboxStore store;

    public NotificationDeliveryAdminController(AcademicNotificationOutboxStore store) {
        this.store = store;
    }

    @GetMapping
    public List<DeadLetter> list(JwtAuthenticationToken authentication) {
        return store.deadLetters(AuthenticatedAcademicActor.organizationId(authentication));
    }

    @PostMapping("/{source}/{id}/requeue")
    public ResponseEntity<Void> requeue(@PathVariable Source source, @PathVariable @Positive long id,
                                        JwtAuthenticationToken authentication) {
        boolean changed = store.requeue(AuthenticatedAcademicActor.organizationId(authentication),
                AuthenticatedAcademicActor.userId(authentication), source, id, OffsetDateTime.now());
        return changed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
