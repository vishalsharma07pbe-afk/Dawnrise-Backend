package com.dawnrise.identity.notification.email;

import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/notification-email/dead-letters")
@PreAuthorize("hasAuthority('SECURITY_AUDIT_VIEW')")
public class NotificationEmailAdminController {
    private final NotificationEmailOutboxStore store;
    public NotificationEmailAdminController(NotificationEmailOutboxStore store){this.store=store;}
    @GetMapping public List<NotificationEmailOutboxStore.DeadLetter> list(@AuthenticationPrincipal Jwt jwt){
        return store.deadLetters(organizationId(jwt));
    }
    @PostMapping("/{id}/requeue") public ResponseEntity<Void> requeue(@PathVariable UUID id,
                                                                       @AuthenticationPrincipal Jwt jwt){
        return store.requeue(organizationId(jwt),userId(jwt),id,OffsetDateTime.now())
                ?ResponseEntity.noContent().build():ResponseEntity.notFound().build();
    }
    private long organizationId(Jwt jwt){Number value=jwt.getClaim("organizationId");
        if(value==null||value.longValue()<=0) throw new org.springframework.security.access.AccessDeniedException("Organization required");
        return value.longValue();}
    private long userId(Jwt jwt){try{long value=Long.parseLong(jwt.getSubject());if(value<=0)throw new NumberFormatException();return value;}
        catch(RuntimeException exception){throw new org.springframework.security.access.AccessDeniedException("User required");}}
}
