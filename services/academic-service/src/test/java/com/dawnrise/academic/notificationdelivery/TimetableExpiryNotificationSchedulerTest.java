package com.dawnrise.academic.notificationdelivery;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimetableExpiryNotificationSchedulerTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Test
    void emitsOneAdvancePhaseThroughoutTheThreeDayWindow() {
        assertEquals("TIMETABLE_EXPIRING",
                TimetableExpiryNotificationScheduler.eventType(TODAY, TODAY.plusDays(4)));
        assertEquals("TIMETABLE_EXPIRING",
                TimetableExpiryNotificationScheduler.eventType(TODAY, TODAY.plusDays(2)));
    }

    @Test
    void emitsDistinctFinalAlertOnLastEffectiveDay() {
        assertEquals("TIMETABLE_EXPIRES_TODAY",
                TimetableExpiryNotificationScheduler.eventType(TODAY, TODAY.plusDays(1)));
    }

    @Test
    void ignoresTimetablesOutsideTheWindow() {
        assertNull(TimetableExpiryNotificationScheduler.eventType(TODAY, TODAY.plusDays(5)));
        assertNull(TimetableExpiryNotificationScheduler.eventType(TODAY, TODAY));
    }
}
