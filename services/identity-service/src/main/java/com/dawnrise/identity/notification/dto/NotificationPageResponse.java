package com.dawnrise.identity.notification.dto;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationItemResponse> content, int pageNumber, int pageSize,
        long totalElements, int totalPages, boolean first, boolean last, boolean empty
) {
    public NotificationPageResponse { content = List.copyOf(content); }
}
