package com.dawnrise.academic.notificationdelivery;

import com.dawnrise.academic.notificationdelivery.AcademicNotificationOutboxStore.CoverContext;
import com.dawnrise.academic.notificationdelivery.AcademicNotificationOutboxStore.Item;
import com.dawnrise.academic.notificationdelivery.AcademicNotificationOutboxStore.Source;
import com.dawnrise.academic.notificationdelivery.AcademicNotificationOutboxStore.TimetableAudience;
import com.dawnrise.academic.notificationdelivery.AcademicNotificationOutboxStore.ExaminationAudience;
import com.dawnrise.academic.results.integration.identity.IdentityGuardianAccessClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Component
@EnableConfigurationProperties(AcademicNotificationDeliveryProperties.class)
@ConditionalOnProperty(name = "dawnrise.notification-delivery.enabled", havingValue = "true", matchIfMissing = true)
public class AcademicNotificationDeliveryWorker {
    private static final Logger log = LoggerFactory.getLogger(AcademicNotificationDeliveryWorker.class);
    private final AcademicNotificationOutboxStore store;
    private final IdentityNotificationClient identity;
    private final IdentityGuardianAccessClient guardians;
    private final AcademicNotificationDeliveryProperties properties;
    private final Clock clock;

    @Autowired
    public AcademicNotificationDeliveryWorker(AcademicNotificationOutboxStore store,
                                              IdentityNotificationClient identity,
                                              IdentityGuardianAccessClient guardians,
                                              AcademicNotificationDeliveryProperties properties) {
        this(store, identity, guardians, properties, Clock.systemUTC());
    }

    AcademicNotificationDeliveryWorker(AcademicNotificationOutboxStore store,
                                       IdentityNotificationClient identity,
                                       IdentityGuardianAccessClient guardians,
                                       AcademicNotificationDeliveryProperties properties,
                                       Clock clock) {
        this.store = store;
        this.identity = identity;
        this.guardians = guardians;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${dawnrise.notification-delivery.delay:PT15S}",
            initialDelayString = "${dawnrise.notification-delivery.initial-delay:PT15S}")
    public void deliver() {
        UUID owner = UUID.randomUUID();
        for (Source source : Source.values()) {
            OffsetDateTime now = now();
            for (Item item : store.claim(source, owner, now, properties.getLeaseDuration(),
                    properties.getBatchSize())) {
                deliver(item, owner);
            }
        }
    }

    void deliver(Item item, UUID owner) {
        try {
            Set<Long> recipients = store.snapshotRecipients(item, resolveRecipients(item));
            IdentityNotificationClient.NotificationRequest request = request(item, recipients);
            identity.ingest(request);
            if (item.source() == Source.DAILY_COVER) {
                identity.resolveGroup(item.organizationId(), request.groupingKey(), request.sourceEventId());
            }
            store.markPublished(item, owner, now());
        } catch (RuntimeException exception) {
            log.warn("Academic notification delivery failed for {} outbox row {}",
                    item.source(), item.id(), exception);
            store.recordFailure(item, owner, now(), properties.getMaximumAttempts(),
                    properties.getBaseBackoff(), exception);
        }
    }

    private Set<Long> resolveRecipients(Item item) {
        if (item.recipientUserIds() != null && !item.recipientUserIds().isEmpty()) {
            return item.recipientUserIds();
        }
        if (item.source() == Source.ATTENDANCE || item.source() == Source.PUBLICATION) {
            LinkedHashSet<Long> recipients = new LinkedHashSet<>();
            recipients.add(item.studentUserId());
            recipients.addAll(guardians.activeGuardianUserIds(item.organizationId(), item.studentUserId()));
            return Set.copyOf(recipients);
        }
        if (item.source() == Source.TIMETABLE_EXPIRY) {
            LinkedHashSet<Long> recipients = new LinkedHashSet<>(identity.resolveRoles(
                    item.organizationId(), Set.of("ADMIN", "PRINCIPAL", "VICE_PRINCIPAL")));
            if ("TIMETABLE_PUBLISHED".equals(item.eventType())
                    || "TIMETABLE_CHANGED".equals(item.eventType())) {
                TimetableAudience audience = store.timetableAudience(item.organizationId(), item.artifactId());
                recipients.addAll(audience.teacherUserIds());
                recipients.addAll(audience.studentUserIds());
                for (Long studentUserId : audience.studentUserIds()) {
                    recipients.addAll(guardians.activeGuardianUserIds(item.organizationId(), studentUserId));
                }
            }
            return Set.copyOf(recipients);
        }
        if (item.source() == Source.EXAMINATION) {
            LinkedHashSet<Long> recipients = new LinkedHashSet<>(identity.resolveRoles(
                    item.organizationId(), Set.of("ADMIN", "PRINCIPAL", "VICE_PRINCIPAL")));
            if (item.actorUserId() != null) recipients.add(item.actorUserId());
            ExaminationAudience audience = store.examinationAudience(item.organizationId(),
                    item.academicYearId(), item.artifactId());
            recipients.addAll(audience.teacherUserIds());
            recipients.addAll(audience.studentUserIds());
            for (Long studentUserId : audience.studentUserIds()) {
                recipients.addAll(guardians.activeGuardianUserIds(item.organizationId(), studentUserId));
            }
            return Set.copyOf(recipients);
        }
        CoverContext context = store.coverContext(item.organizationId(), item.caseId());
        return "CONFIRMED_SUBSTITUTION".equals(item.eventType())
                ? context.confirmedRecipients() : context.managementRecipients();
    }

    private IdentityNotificationClient.NotificationRequest request(Item item, Set<Long> recipients) {
        return switch (item.source()) {
            case ATTENDANCE -> attendance(item, recipients);
            case PUBLICATION -> publication(item, recipients);
            case DAILY_COVER -> coverage(item, recipients);
            case TIMETABLE_EXPIRY -> timetableExpiry(item, recipients);
            case EXAMINATION -> examination(item, recipients);
        };
    }

    private IdentityNotificationClient.NotificationRequest examination(Item item, Set<Long> recipients) {
        return new IdentityNotificationClient.NotificationRequest(item.organizationId(), item.eventId().toString(),
                "EXAM_SCHEDULE_PUBLISHED", "SCHOOL", "IMPORTANT", "Exam date sheet published",
                "A new examination schedule is available.",
                new IdentityNotificationClient.Action("EXAMINATION", item.artifactId(), null,
                        item.academicYearId()), "examination:" + item.artifactId(),
                item.occurredAt(), recipients);
    }

    private IdentityNotificationClient.NotificationRequest timetableExpiry(Item item, Set<Long> recipients) {
        boolean published = "TIMETABLE_PUBLISHED".equals(item.eventType());
        boolean changed = "TIMETABLE_CHANGED".equals(item.eventType());
        boolean finalDay = "TIMETABLE_EXPIRES_TODAY".equals(item.eventType());
        return new IdentityNotificationClient.NotificationRequest(item.organizationId(), item.eventId().toString(),
                item.eventType(), "TIMETABLE", finalDay ? "URGENT" : "IMPORTANT",
                published ? "New timetable published" : changed ? "Timetable updated"
                        : finalDay ? "Timetable expires today" : "Timetable expires soon",
                published ? "A new timetable is available for your school."
                        : changed ? "Your published timetable has changed."
                        : finalDay ? "Publish the next timetable to avoid a scheduling gap."
                        : "The current timetable has three days or fewer remaining.",
                new IdentityNotificationClient.Action("TIMETABLE", item.artifactId(), null,
                        item.academicYearId()), "timetable-expiry:" + item.artifactId(),
                item.occurredAt(), recipients);
    }

    private IdentityNotificationClient.NotificationRequest attendance(Item item, Set<Long> recipients) {
        boolean correction = item.eventType().equals("ATTENDANCE_ALERT_CLEARED")
                || item.eventType().contains("CORRECTION")
                || item.payload().contains("\"eventSource\":\"CORRECTION_APPROVAL\"");
        return new IdentityNotificationClient.NotificationRequest(item.organizationId(),
                item.eventId().toString(), correction ? "ATTENDANCE_CORRECTED" : "ATTENDANCE_RECORDED",
                "ATTENDANCE", "NORMAL", correction ? "Attendance updated" : "Attendance recorded",
                "A student attendance record has been updated.",
                new IdentityNotificationClient.Action("ATTENDANCE", item.id(), item.studentUserId(),
                        item.academicYearId()),
                "attendance:" + item.organizationId() + ":" + item.studentUserId(), item.occurredAt(), recipients);
    }

    private IdentityNotificationClient.NotificationRequest publication(Item item, Set<Long> recipients) {
        boolean reportCard = "REPORT_CARD_PUBLISHED".equals(item.eventType());
        return new IdentityNotificationClient.NotificationRequest(item.organizationId(), item.eventId().toString(),
                item.eventType(), "RESULTS", "NORMAL",
                reportCard ? "Report card published" : "Results published",
                reportCard ? "A new report card is ready to view." : "New results are ready to view.",
                new IdentityNotificationClient.Action(reportCard ? "REPORT_CARD" : "PUBLISHED_RESULT",
                        item.artifactId(), item.studentUserId(), item.academicYearId()),
                (reportCard ? "report-card:" : "result:") + item.artifactId() + ":" + item.studentUserId(),
                item.occurredAt(), recipients);
    }

    private IdentityNotificationClient.NotificationRequest coverage(Item item, Set<Long> recipients) {
        CoverContext context = store.coverContext(item.organizationId(), item.caseId());
        String type = item.eventType();
        String heading = switch (type) {
            case "COVER_NEEDED" -> "Cover needed";
            case "APPROVAL_REQUIRED" -> "Cover approval required";
            case "CONFIRMED_SUBSTITUTION" -> "Substitution confirmed";
            case "IMMINENT_UNCOVERED" -> "Class still uncovered";
            case "CLASS_UNCOVERED" -> "Class has started uncovered";
            default -> "Cover arrangement changed";
        };
        String importance = switch (type) {
            case "IMMINENT_UNCOVERED", "CLASS_UNCOVERED" -> "URGENT";
            case "COVER_NEEDED", "APPROVAL_REQUIRED", "COVER_CHANGED" -> "IMPORTANT";
            default -> "NORMAL";
        };
        String preview = context.sectionName() + " · " + context.coverDate() + " · " + context.startTime();
        return new IdentityNotificationClient.NotificationRequest(item.organizationId(), item.eventId().toString(),
                type, "TIMETABLE", importance, heading, preview,
                new IdentityNotificationClient.Action("DAILY_COVERAGE", item.caseId(), null,
                        context.academicYearId()), "daily-cover:" + item.caseId(), item.occurredAt(), recipients);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
