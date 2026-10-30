package com.dawnrise.identity.notification.dto;

import com.dawnrise.identity.notification.model.NotificationEmailMode;
import java.time.LocalTime;
import java.util.Set;

public record NotificationPreferencesResponse(
        boolean browserNotificationsEnabled, boolean soundsEnabled,
        boolean timetableEnabled, boolean attendanceEnabled, boolean resultsEnabled,
        boolean securityEnabled, boolean schoolEnabled, boolean approvalsEnabled, boolean leaveEnabled,
        NotificationEmailMode timetableEmailMode, NotificationEmailMode attendanceEmailMode,
        NotificationEmailMode resultsEmailMode, NotificationEmailMode approvalsEmailMode,
        NotificationEmailMode schoolEmailMode, NotificationEmailMode leaveEmailMode, boolean quietHoursEnabled,
        LocalTime quietHoursStart, LocalTime quietHoursEnd, String timeZone,
        Set<String> mandatoryInAppCategories, Set<String> mandatoryEmailEvents, long version
) {}
