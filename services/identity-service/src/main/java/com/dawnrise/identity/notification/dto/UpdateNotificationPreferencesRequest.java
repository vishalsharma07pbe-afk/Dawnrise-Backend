package com.dawnrise.identity.notification.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotBlank;
import com.dawnrise.identity.notification.model.NotificationEmailMode;
import java.time.LocalTime;

public record UpdateNotificationPreferencesRequest(
        boolean browserNotificationsEnabled, boolean soundsEnabled,
        boolean timetableEnabled, boolean attendanceEnabled, boolean resultsEnabled,
        boolean securityEnabled, boolean schoolEnabled, boolean approvalsEnabled, boolean leaveEnabled,
        NotificationEmailMode timetableEmailMode, NotificationEmailMode attendanceEmailMode,
        NotificationEmailMode resultsEmailMode, NotificationEmailMode approvalsEmailMode,
        NotificationEmailMode schoolEmailMode, NotificationEmailMode leaveEmailMode, boolean quietHoursEnabled,
        LocalTime quietHoursStart, LocalTime quietHoursEnd, @NotBlank String timeZone,
        @PositiveOrZero long version
) {}
