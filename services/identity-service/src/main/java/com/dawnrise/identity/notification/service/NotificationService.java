package com.dawnrise.identity.notification.service;

import com.dawnrise.identity.notification.dto.IngestNotificationRequest;
import com.dawnrise.identity.notification.dto.IngestNotificationResponse;
import com.dawnrise.identity.notification.dto.NotificationDeleteResponse;
import com.dawnrise.identity.notification.dto.NotificationItemResponse;
import com.dawnrise.identity.notification.dto.NotificationPageResponse;
import com.dawnrise.identity.notification.dto.NotificationPreferencesResponse;
import com.dawnrise.identity.notification.dto.UpdateNotificationPreferencesRequest;
import com.dawnrise.identity.notification.dto.ResolveNotificationGroupResponse;
import com.dawnrise.identity.notification.model.NotificationCategory;
import com.dawnrise.identity.notification.model.NotificationFilter;

import java.util.List;
import java.util.UUID;

public interface NotificationService {
    NotificationPageResponse inbox(long organizationId, long userId, NotificationFilter filter,
                                   NotificationCategory category, int page, int size);
    List<NotificationItemResponse> navbarPreview(long organizationId, long userId);
    long unreadCount(long organizationId, long userId);
    NotificationItemResponse detail(long organizationId, long userId, UUID notificationId);
    NotificationItemResponse markRead(long organizationId, long userId, UUID notificationId);
    long markAllRead(long organizationId, long userId);
    NotificationItemResponse hideFromNavbar(long organizationId, long userId, UUID notificationId);
    NotificationDeleteResponse delete(long organizationId, long userId, UUID notificationId);
    NotificationItemResponse undoDelete(long organizationId, long userId, UUID notificationId);
    NotificationPreferencesResponse preferences(long organizationId, long userId);
    NotificationPreferencesResponse updatePreferences(long organizationId, long userId,
                                                       UpdateNotificationPreferencesRequest request);
    IngestNotificationResponse ingest(String sourceService, IngestNotificationRequest request);

    ResolveNotificationGroupResponse resolveGroup(String sourceService, long organizationId,
                                                   String groupingKey, String excludeSourceEventId);
}
