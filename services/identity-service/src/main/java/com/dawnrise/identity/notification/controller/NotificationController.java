package com.dawnrise.identity.notification.controller;

import com.dawnrise.identity.notification.dto.NotificationDeleteResponse;
import com.dawnrise.identity.notification.dto.NotificationItemResponse;
import com.dawnrise.identity.notification.dto.NotificationPageResponse;
import com.dawnrise.identity.notification.dto.NotificationPreferencesResponse;
import com.dawnrise.identity.notification.dto.NotificationUnreadCountResponse;
import com.dawnrise.identity.notification.dto.UpdateNotificationPreferencesRequest;
import com.dawnrise.identity.notification.model.NotificationCategory;
import com.dawnrise.identity.notification.model.NotificationFilter;
import com.dawnrise.identity.notification.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/notifications")
@PreAuthorize("@notificationSecurity.isOrganizationUser(authentication)")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public NotificationPageResponse inbox(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "ALL") NotificationFilter filter,
            @RequestParam(required = false) NotificationCategory category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return notifications.inbox(organizationId(jwt), userId(jwt), filter, category, page, size);
    }

    @GetMapping("/navbar-preview")
    public List<NotificationItemResponse> navbarPreview(@AuthenticationPrincipal Jwt jwt) {
        return notifications.navbarPreview(organizationId(jwt), userId(jwt));
    }

    @GetMapping("/unread-count")
    public NotificationUnreadCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new NotificationUnreadCountResponse(
                notifications.unreadCount(organizationId(jwt), userId(jwt)));
    }

    @GetMapping("/{notificationId}")
    public NotificationItemResponse detail(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID notificationId) {
        return notifications.detail(organizationId(jwt), userId(jwt), notificationId);
    }

    @PostMapping("/{notificationId}/read")
    public NotificationItemResponse markRead(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID notificationId) {
        return notifications.markRead(organizationId(jwt), userId(jwt), notificationId);
    }

    @PostMapping("/read-all")
    public NotificationUnreadCountResponse markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return new NotificationUnreadCountResponse(
                notifications.markAllRead(organizationId(jwt), userId(jwt)));
    }

    @PostMapping("/{notificationId}/hide-from-navbar")
    public NotificationItemResponse hideFromNavbar(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID notificationId) {
        return notifications.hideFromNavbar(organizationId(jwt), userId(jwt), notificationId);
    }

    @DeleteMapping("/{notificationId}")
    public NotificationDeleteResponse delete(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID notificationId) {
        return notifications.delete(organizationId(jwt), userId(jwt), notificationId);
    }

    @PostMapping("/{notificationId}/undo-delete")
    public NotificationItemResponse undoDelete(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable UUID notificationId) {
        return notifications.undoDelete(organizationId(jwt), userId(jwt), notificationId);
    }

    @GetMapping("/preferences/self")
    public NotificationPreferencesResponse preferences(@AuthenticationPrincipal Jwt jwt) {
        return notifications.preferences(organizationId(jwt), userId(jwt));
    }

    @PutMapping("/preferences/self")
    public NotificationPreferencesResponse updatePreferences(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateNotificationPreferencesRequest request) {
        return notifications.updatePreferences(organizationId(jwt), userId(jwt), request);
    }

    private long organizationId(Jwt jwt) {
        if (!"ORGANIZATION_USER".equals(jwt.getClaimAsString("identityType"))) {
            throw new AccessDeniedException("Organization notification access is required");
        }
        Object claim = jwt.getClaim("organizationId");
        if (!(claim instanceof Number number) || number.longValue() <= 0) {
            throw new AccessDeniedException("Organization notification access is required");
        }
        return number.longValue();
    }

    private long userId(Jwt jwt) {
        try {
            long id = Long.parseLong(jwt.getSubject());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("Organization notification access is required");
        }
    }
}
