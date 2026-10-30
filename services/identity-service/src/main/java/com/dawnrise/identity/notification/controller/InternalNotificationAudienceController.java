package com.dawnrise.identity.notification.controller;
import com.dawnrise.identity.notification.dto.ResolveNotificationAudienceRequest;
import com.dawnrise.identity.notification.dto.ResolveNotificationAudienceResponse;
import com.dawnrise.identity.notification.service.NotificationAudienceResolver;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/v1/organizations/{organizationId}/notification-audience")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalNotificationAudienceController {
    private final NotificationAudienceResolver resolver;
    public InternalNotificationAudienceController(NotificationAudienceResolver resolver){this.resolver=resolver;}
    @PostMapping("/resolve") public ResolveNotificationAudienceResponse resolve(@PathVariable long organizationId,@Valid @RequestBody ResolveNotificationAudienceRequest request){return new ResolveNotificationAudienceResponse(resolver.resolve(organizationId,request));}
}
