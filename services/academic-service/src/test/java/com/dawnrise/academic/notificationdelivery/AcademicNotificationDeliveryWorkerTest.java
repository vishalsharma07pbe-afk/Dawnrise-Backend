package com.dawnrise.academic.notificationdelivery;

import com.dawnrise.academic.notificationdelivery.AcademicNotificationOutboxStore.CoverContext;
import com.dawnrise.academic.notificationdelivery.AcademicNotificationOutboxStore.Item;
import com.dawnrise.academic.notificationdelivery.AcademicNotificationOutboxStore.Source;
import com.dawnrise.academic.notificationdelivery.AcademicNotificationOutboxStore.TimetableAudience;
import com.dawnrise.academic.notificationdelivery.AcademicNotificationOutboxStore.ExaminationAudience;
import com.dawnrise.academic.results.integration.identity.IdentityGuardianAccessClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcademicNotificationDeliveryWorkerTest {
    private AcademicNotificationOutboxStore store;
    private IdentityNotificationClient identity;
    private IdentityGuardianAccessClient guardians;
    private AcademicNotificationDeliveryWorker worker;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-29T08:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        store = mock(AcademicNotificationOutboxStore.class);
        identity = mock(IdentityNotificationClient.class);
        guardians = mock(IdentityGuardianAccessClient.class);
        AcademicNotificationDeliveryProperties properties = new AcademicNotificationDeliveryProperties();
        worker = new AcademicNotificationDeliveryWorker(store, identity, guardians, properties, clock);
    }

    @Test
    void studentDeliveryTargetsStudentAndAllActiveGuardiansWithoutDuplicates() {
        UUID owner = UUID.randomUUID();
        Item item = item(Source.PUBLICATION, "RESULT_PUBLISHED", 21L, null, 40L);
        when(guardians.activeGuardianUserIds(1, 21)).thenReturn(List.of(90L, 91L, 90L));
        when(store.snapshotRecipients(eq(item), any())).thenAnswer(invocation -> invocation.getArgument(1));

        worker.deliver(item, owner);

        ArgumentCaptor<IdentityNotificationClient.NotificationRequest> request =
                ArgumentCaptor.forClass(IdentityNotificationClient.NotificationRequest.class);
        verify(identity).ingest(request.capture());
        assertEquals(Set.of(21L, 90L, 91L), request.getValue().recipientUserIds());
        assertEquals("PUBLISHED_RESULT", request.getValue().action().type());
        verify(store).markPublished(item, owner, OffsetDateTime.now(clock));
    }

    @Test
    void confirmedCoverIsDistinctAndReachesAssignedSubstituteAfterIngestion() {
        UUID owner = UUID.randomUUID();
        Item item = item(Source.DAILY_COVER, "CONFIRMED_SUBSTITUTION", null, 50L, null);
        CoverContext context = new CoverContext(2, "A", LocalDate.of(2026, 10, 29),
                LocalTime.of(9, 0), 70L, 71L, 72L, 80L);
        when(store.coverContext(1, 50)).thenReturn(context);
        when(store.snapshotRecipients(eq(item), any())).thenAnswer(invocation -> invocation.getArgument(1));

        worker.deliver(item, owner);

        ArgumentCaptor<IdentityNotificationClient.NotificationRequest> request =
                ArgumentCaptor.forClass(IdentityNotificationClient.NotificationRequest.class);
        var order = inOrder(identity, store);
        order.verify(identity).ingest(request.capture());
        order.verify(identity).resolveGroup(1, "daily-cover:50", item.eventId().toString());
        order.verify(store).markPublished(item, owner, OffsetDateTime.now(clock));
        assertEquals(Set.of(70L, 71L, 72L, 80L), request.getValue().recipientUserIds());
        assertEquals("Substitution confirmed", request.getValue().heading());
    }

    @Test
    void pendingProposalIsNeverDescribedAsConfirmed() {
        UUID owner = UUID.randomUUID();
        Item item = item(Source.DAILY_COVER, "APPROVAL_REQUIRED", null, 50L, null);
        when(store.coverContext(1, 50)).thenReturn(new CoverContext(2, "A",
                LocalDate.of(2026, 10, 29), LocalTime.of(9, 0), 70L, 71L, null, 80L));
        when(store.snapshotRecipients(eq(item), any())).thenAnswer(invocation -> invocation.getArgument(1));

        worker.deliver(item, owner);

        ArgumentCaptor<IdentityNotificationClient.NotificationRequest> request =
                ArgumentCaptor.forClass(IdentityNotificationClient.NotificationRequest.class);
        verify(identity).ingest(request.capture());
        assertEquals("Cover approval required", request.getValue().heading());
        assertFalse(request.getValue().heading().toLowerCase().contains("confirmed"));
    }

    @Test
    void timetableExpiryResolvesOnlyActiveTenantAdminsAndUsesTypedTimetableAction() {
        UUID owner = UUID.randomUUID();
        Item item = item(Source.TIMETABLE_EXPIRY, "TIMETABLE_EXPIRING", null, null, 40L);
        when(identity.resolveRoles(1, Set.of("ADMIN", "PRINCIPAL", "VICE_PRINCIPAL")))
                .thenReturn(Set.of(7L, 8L));
        when(store.snapshotRecipients(item, Set.of(7L, 8L))).thenReturn(Set.of(7L, 8L));

        worker.deliver(item, owner);

        ArgumentCaptor<IdentityNotificationClient.NotificationRequest> request =
                ArgumentCaptor.forClass(IdentityNotificationClient.NotificationRequest.class);
        verify(identity).ingest(request.capture());
        assertEquals(Set.of(7L, 8L), request.getValue().recipientUserIds());
        assertEquals("TIMETABLE", request.getValue().action().type());
        assertEquals("Timetable expires soon", request.getValue().heading());
        verify(store).markPublished(item, owner, OffsetDateTime.now(clock));
    }

    @Test
    void finalTimetableExpiryAlertIsUrgentAndDistinct() {
        UUID owner = UUID.randomUUID();
        Item item = item(Source.TIMETABLE_EXPIRY, "TIMETABLE_EXPIRES_TODAY", null, null, 40L);
        when(identity.resolveRoles(1, Set.of("ADMIN", "PRINCIPAL", "VICE_PRINCIPAL")))
                .thenReturn(Set.of(7L));
        when(store.snapshotRecipients(item, Set.of(7L))).thenReturn(Set.of(7L));

        worker.deliver(item, owner);

        ArgumentCaptor<IdentityNotificationClient.NotificationRequest> request =
                ArgumentCaptor.forClass(IdentityNotificationClient.NotificationRequest.class);
        verify(identity).ingest(request.capture());
        assertEquals("URGENT", request.getValue().importance());
        assertEquals("Timetable expires today", request.getValue().heading());
    }

    @Test
    void publishedTimetableTargetsOnlyAffectedPeopleAndLeadershipWithoutDuplicates() {
        UUID owner = UUID.randomUUID();
        Item item = item(Source.TIMETABLE_EXPIRY, "TIMETABLE_CHANGED", null, null, 40L);
        when(identity.resolveRoles(1, Set.of("ADMIN", "PRINCIPAL", "VICE_PRINCIPAL")))
                .thenReturn(Set.of(7L));
        when(store.timetableAudience(1, 40)).thenReturn(
                new TimetableAudience(Set.of(21L, 22L), Set.of(7L, 30L)));
        when(guardians.activeGuardianUserIds(1, 21)).thenReturn(List.of(50L));
        when(guardians.activeGuardianUserIds(1, 22)).thenReturn(List.of(50L, 51L));
        when(store.snapshotRecipients(eq(item), any())).thenAnswer(invocation -> invocation.getArgument(1));

        worker.deliver(item, owner);

        ArgumentCaptor<IdentityNotificationClient.NotificationRequest> request =
                ArgumentCaptor.forClass(IdentityNotificationClient.NotificationRequest.class);
        verify(identity).ingest(request.capture());
        assertEquals(Set.of(7L, 21L, 22L, 30L, 50L, 51L), request.getValue().recipientUserIds());
        assertEquals("Timetable updated", request.getValue().heading());
        assertEquals("TIMETABLE", request.getValue().action().type());
    }

    @Test
    void publishedExamTargetsPublisherAffectedSchoolCommunityAndLeadership() {
        UUID owner = UUID.randomUUID();
        Item item = new Item(Source.EXAMINATION, 1, UUID.randomUUID(), 1, 2L,
                null, null, 44L, 7L, "EXAM_SCHEDULE_PUBLISHED", "{}",
                OffsetDateTime.now(clock), 1, Set.of());
        when(identity.resolveRoles(1, Set.of("ADMIN", "PRINCIPAL", "VICE_PRINCIPAL")))
                .thenReturn(Set.of(7L, 8L));
        when(store.examinationAudience(1, 2, 44)).thenReturn(
                new ExaminationAudience(Set.of(21L), Set.of(30L)));
        when(guardians.activeGuardianUserIds(1, 21)).thenReturn(List.of(50L));
        when(store.snapshotRecipients(eq(item), any())).thenAnswer(invocation -> invocation.getArgument(1));

        worker.deliver(item, owner);

        ArgumentCaptor<IdentityNotificationClient.NotificationRequest> request =
                ArgumentCaptor.forClass(IdentityNotificationClient.NotificationRequest.class);
        verify(identity).ingest(request.capture());
        assertEquals(Set.of(7L, 8L, 21L, 30L, 50L), request.getValue().recipientUserIds());
        assertEquals("EXAMINATION", request.getValue().action().type());
        assertEquals("Exam date sheet published", request.getValue().heading());
    }

    @Test
    void failedIdentityCallLeavesRowForBoundedRetry() {
        UUID owner = UUID.randomUUID();
        Item item = item(Source.ATTENDANCE, "ABSENCE_RECORDED", 21L, null, null);
        when(guardians.activeGuardianUserIds(1, 21)).thenReturn(List.of());
        when(store.snapshotRecipients(eq(item), any())).thenReturn(Set.of(21L));
        org.mockito.Mockito.doThrow(new NotificationDeliveryException("unavailable", null))
                .when(identity).ingest(any());

        worker.deliver(item, owner);

        verify(store).recordFailure(eq(item), eq(owner), eq(OffsetDateTime.now(clock)),
                eq(5), eq(java.time.Duration.ofSeconds(30)), any());
        verify(store, never()).markPublished(any(), any(), any());
    }

    @Test
    void retryAfterAcceptedIngestionRemainsIdempotentAndEventuallyPublishes() {
        UUID owner = UUID.randomUUID();
        Item item = item(Source.DAILY_COVER, "CONFIRMED_SUBSTITUTION", null, 50L, null);
        when(store.coverContext(1, 50)).thenReturn(new CoverContext(2, "A",
                LocalDate.of(2026, 10, 29), LocalTime.of(9, 0), 70L, 71L, 72L, 80L));
        when(store.snapshotRecipients(eq(item), any())).thenAnswer(invocation -> invocation.getArgument(1));
        org.mockito.Mockito.doThrow(new NotificationDeliveryException("resolve unavailable", null))
                .doNothing().when(identity).resolveGroup(1, "daily-cover:50", item.eventId().toString());

        worker.deliver(item, owner);
        worker.deliver(item, owner);

        verify(identity, times(2)).ingest(any());
        verify(identity, times(2)).resolveGroup(1, "daily-cover:50", item.eventId().toString());
        verify(store, times(1)).recordFailure(eq(item), eq(owner), eq(OffsetDateTime.now(clock)),
                eq(5), eq(java.time.Duration.ofSeconds(30)), any());
        verify(store, times(1)).markPublished(item, owner, OffsetDateTime.now(clock));
    }

    private Item item(Source source, String type, Long student, Long coverCase, Long artifact) {
        return new Item(source, 1, UUID.randomUUID(), 1, 2L, student, coverCase, artifact, null,
                type, "{}", OffsetDateTime.now(clock), 1, Set.of());
    }
}
