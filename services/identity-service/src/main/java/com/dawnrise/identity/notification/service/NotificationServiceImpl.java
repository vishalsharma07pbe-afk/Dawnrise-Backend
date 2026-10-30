package com.dawnrise.identity.notification.service;

import com.dawnrise.identity.notification.dto.IngestNotificationRequest;
import com.dawnrise.identity.notification.dto.IngestNotificationResponse;
import com.dawnrise.identity.notification.dto.NotificationAction;
import com.dawnrise.identity.notification.dto.NotificationDeleteResponse;
import com.dawnrise.identity.notification.dto.NotificationItemResponse;
import com.dawnrise.identity.notification.dto.NotificationPageResponse;
import com.dawnrise.identity.notification.dto.NotificationPreferencesResponse;
import com.dawnrise.identity.notification.dto.UpdateNotificationPreferencesRequest;
import com.dawnrise.identity.notification.dto.ResolveNotificationGroupResponse;
import com.dawnrise.identity.notification.exception.NotificationNotFoundException;
import com.dawnrise.identity.notification.model.NotificationActionType;
import com.dawnrise.identity.notification.model.NotificationCategory;
import com.dawnrise.identity.notification.model.NotificationEventType;
import com.dawnrise.identity.notification.model.NotificationFilter;
import com.dawnrise.identity.notification.model.NotificationImportance;
import com.dawnrise.identity.notification.model.NotificationEmailMode;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;

@Service
public class NotificationServiceImpl implements NotificationService {
    private static final int NAVBAR_LIMIT = 5;
    private static final int UNDO_SECONDS = 30;
    private static final Set<NotificationEventType> ACADEMIC_EVENTS = EnumSet.of(
            NotificationEventType.COVER_NEEDED, NotificationEventType.APPROVAL_REQUIRED,
            NotificationEventType.CONFIRMED_SUBSTITUTION, NotificationEventType.IMMINENT_UNCOVERED,
            NotificationEventType.CLASS_UNCOVERED, NotificationEventType.COVER_CHANGED,
            NotificationEventType.ATTENDANCE_RECORDED, NotificationEventType.ATTENDANCE_CORRECTED,
            NotificationEventType.RESULT_PUBLISHED, NotificationEventType.REPORT_CARD_PUBLISHED,
            NotificationEventType.TIMETABLE_PUBLISHED, NotificationEventType.TIMETABLE_CHANGED,
            NotificationEventType.TIMETABLE_EXPIRING, NotificationEventType.TIMETABLE_EXPIRES_TODAY,
            NotificationEventType.EXAM_SCHEDULE_PUBLISHED,
            NotificationEventType.LEAVE_REQUESTED, NotificationEventType.LEAVE_APPROVAL_REQUIRED,
            NotificationEventType.LEAVE_APPROVED, NotificationEventType.LEAVE_REJECTED,
            NotificationEventType.LEAVE_CANCELLED);
    private static final Set<NotificationEventType> SCHOOL_EVENTS = EnumSet.of(
            NotificationEventType.SCHOOL_ANNOUNCEMENT, NotificationEventType.DAWNRISE_RELEASE,
            NotificationEventType.SCHOOL_PROVISIONING_COMPLETED,
            NotificationEventType.SCHOOL_PROVISIONING_FAILED);
    private static final Set<NotificationEventType> IDENTITY_EVENTS =
            EnumSet.of(NotificationEventType.SECURITY_ALERT, NotificationEventType.ACCOUNT_LOCKED,
                    NotificationEventType.ACCOUNT_STATUS_CHANGED,
                    NotificationEventType.ROLE_ASSIGNMENT_REQUESTED,
                    NotificationEventType.ROLE_ASSIGNMENT_APPROVAL_REQUIRED,
                    NotificationEventType.ROLE_ASSIGNMENT_APPROVED,
                    NotificationEventType.ROLE_ASSIGNMENT_REJECTED,
                    NotificationEventType.ROLE_ASSIGNMENT_CANCELLED,
                    NotificationEventType.ROLE_REMOVAL_REQUESTED,
                    NotificationEventType.ROLE_REMOVAL_APPROVAL_REQUIRED,
                    NotificationEventType.ROLE_REMOVAL_APPROVED,
                    NotificationEventType.ROLE_REMOVAL_REJECTED,
                    NotificationEventType.ROLE_REMOVAL_CANCELLED);

    private static final String ITEM_SELECT = """
            SELECT r.id, e.event_type, e.category, e.importance, e.heading, e.context,
                   e.action_type, e.action_reference_id, e.action_student_user_id,
                   e.action_academic_year_id, e.grouping_key, e.occurred_at,
                   r.created_at, r.read_at, r.navbar_hidden_at
              FROM notification_recipients r
              JOIN notification_events e
                ON e.id = r.event_id AND e.organization_id = r.organization_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final UserRepository users;
    private final Clock clock;

    @Autowired
    public NotificationServiceImpl(NamedParameterJdbcTemplate jdbc, UserRepository users) {
        this(jdbc, users, Clock.systemUTC());
    }

    NotificationServiceImpl(NamedParameterJdbcTemplate jdbc, UserRepository users, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPageResponse inbox(long organizationId, long userId,
                                           NotificationFilter filter,
                                           NotificationCategory category,
                                           int page, int size) {
        MapSqlParameterSource parameters = scope(organizationId, userId)
                .addValue("limit", size).addValue("offset", page * size);
        String conditions = filterConditions(filter, category, parameters);
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification_recipients r
                JOIN notification_events e ON e.id=r.event_id AND e.organization_id=r.organization_id
                WHERE r.organization_id=:organizationId AND r.user_id=:userId
                  AND r.deleted_at IS NULL
                  AND NOT r.inbox_suppressed
                """ + conditions, parameters, Long.class);
        List<NotificationItemResponse> content = jdbc.query(ITEM_SELECT + """
                WHERE r.organization_id=:organizationId AND r.user_id=:userId
                  AND r.deleted_at IS NULL
                """ + conditions + """
                ORDER BY e.occurred_at DESC, r.id DESC
                LIMIT :limit OFFSET :offset
                """, parameters, this::mapItem);
        long count = total == null ? 0 : total;
        int totalPages = count == 0 ? 0 : (int) ((count + size - 1) / size);
        return new NotificationPageResponse(content, page, size, count, totalPages,
                page == 0, page + 1 >= totalPages, content.isEmpty());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationItemResponse> navbarPreview(long organizationId, long userId) {
        return jdbc.query(ITEM_SELECT + """
                WHERE r.organization_id=:organizationId AND r.user_id=:userId
                  AND r.deleted_at IS NULL AND r.navbar_hidden_at IS NULL
                  AND NOT r.inbox_suppressed
                ORDER BY e.occurred_at DESC, r.id DESC LIMIT :limit
                """, scope(organizationId, userId).addValue("limit", NAVBAR_LIMIT), this::mapItem);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(long organizationId, long userId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification_recipients
                WHERE organization_id=:organizationId AND user_id=:userId
                  AND read_at IS NULL AND deleted_at IS NULL
                  AND NOT inbox_suppressed
                """, scope(organizationId, userId), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationItemResponse detail(long organizationId, long userId, UUID notificationId) {
        List<NotificationItemResponse> results = jdbc.query(ITEM_SELECT + """
                WHERE r.organization_id=:organizationId AND r.user_id=:userId
                  AND r.id=:notificationId AND r.deleted_at IS NULL
                """, scopedId(organizationId, userId, notificationId), this::mapItem);
        if (results.isEmpty()) throw new NotificationNotFoundException();
        return results.getFirst();
    }

    @Override
    @Transactional
    public NotificationItemResponse markRead(long organizationId, long userId, UUID notificationId) {
        NotificationItemResponse existing = detail(organizationId, userId, notificationId);
        if (existing.readAt() == null) {
            jdbc.update("""
                    UPDATE notification_recipients SET read_at=:now, updated_at=:now, version=version+1
                    WHERE organization_id=:organizationId AND user_id=:userId AND id=:notificationId
                      AND deleted_at IS NULL AND read_at IS NULL
                    """, scopedId(organizationId, userId, notificationId).addValue("now", now()));
        }
        return detail(organizationId, userId, notificationId);
    }

    @Override
    @Transactional
    public long markAllRead(long organizationId, long userId) {
        OffsetDateTime cutoff = now();
        jdbc.update("""
                UPDATE notification_recipients SET read_at=:cutoff, updated_at=:cutoff, version=version+1
                WHERE organization_id=:organizationId AND user_id=:userId
                  AND deleted_at IS NULL AND read_at IS NULL AND created_at <= :cutoff
                """, scope(organizationId, userId).addValue("cutoff", cutoff));
        return unreadCount(organizationId, userId);
    }

    @Override
    @Transactional
    public NotificationItemResponse hideFromNavbar(long organizationId, long userId, UUID notificationId) {
        requireMutable(organizationId, userId, notificationId);
        OffsetDateTime now = now();
        jdbc.update("""
                UPDATE notification_recipients
                   SET navbar_hidden_at=COALESCE(navbar_hidden_at,:now), updated_at=:now,
                       version=CASE WHEN navbar_hidden_at IS NULL THEN version+1 ELSE version END
                 WHERE organization_id=:organizationId AND user_id=:userId
                   AND id=:notificationId AND deleted_at IS NULL
                """, scopedId(organizationId, userId, notificationId).addValue("now", now));
        return detail(organizationId, userId, notificationId);
    }

    @Override
    @Transactional
    public NotificationDeleteResponse delete(long organizationId, long userId, UUID notificationId) {
        requireMutable(organizationId, userId, notificationId);
        OffsetDateTime deletedAt = now();
        OffsetDateTime undoUntil = deletedAt.plusSeconds(UNDO_SECONDS);
        int changed = jdbc.update("""
                UPDATE notification_recipients
                   SET deleted_at=:deletedAt, delete_undo_until=:undoUntil,
                       updated_at=:deletedAt, version=version+1
                 WHERE organization_id=:organizationId AND user_id=:userId
                   AND id=:notificationId AND deleted_at IS NULL
                """, scopedId(organizationId, userId, notificationId)
                .addValue("deletedAt", deletedAt).addValue("undoUntil", undoUntil));
        if (changed == 0) throw new NotificationNotFoundException();
        return new NotificationDeleteResponse(notificationId, undoUntil);
    }

    @Override
    @Transactional
    public NotificationItemResponse undoDelete(long organizationId, long userId, UUID notificationId) {
        OffsetDateTime now = now();
        int changed = jdbc.update("""
                UPDATE notification_recipients
                   SET deleted_at=NULL, delete_undo_until=NULL, updated_at=:now, version=version+1
                 WHERE organization_id=:organizationId AND user_id=:userId AND id=:notificationId
                   AND deleted_at IS NOT NULL AND delete_undo_until >= :now
                """, scopedId(organizationId, userId, notificationId).addValue("now", now));
        if (changed == 0) throw new NotificationNotFoundException();
        return detail(organizationId, userId, notificationId);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferencesResponse preferences(long organizationId, long userId) {
        List<NotificationPreferencesResponse> result = jdbc.query("""
                SELECT browser_notifications_enabled, sounds_enabled, timetable_enabled,
                       attendance_enabled, results_enabled, security_enabled, school_enabled,
                       approvals_enabled,leave_enabled,timetable_email_mode,attendance_email_mode,results_email_mode,
                       approvals_email_mode,school_email_mode,leave_email_mode,quiet_hours_enabled,quiet_hours_start,
                       quiet_hours_end,time_zone,version
                FROM notification_preferences
                WHERE organization_id=:organizationId AND user_id=:userId
                """, scope(organizationId, userId), (rs, row) -> mapPreferences(rs));
        return result.isEmpty() ? defaults() : result.getFirst();
    }

    @Override
    @Transactional
    public NotificationPreferencesResponse updatePreferences(
            long organizationId, long userId, UpdateNotificationPreferencesRequest request) {
        users.findByOrganizationIdAndId(organizationId, userId)
                .orElseThrow(NotificationNotFoundException::new);
        MapSqlParameterSource parameters = preferenceParameters(organizationId, userId, request);
        int changed = jdbc.update("""
                UPDATE notification_preferences SET
                    browser_notifications_enabled=:browser, sounds_enabled=:sounds,
                    timetable_enabled=:timetable, attendance_enabled=:attendance,
                    results_enabled=:results, security_enabled=:security, school_enabled=:school,
                    approvals_enabled=:approvals,leave_enabled=:leave,timetable_email_mode=:timetableEmail,
                    attendance_email_mode=:attendanceEmail,results_email_mode=:resultsEmail,
                    approvals_email_mode=:approvalsEmail,school_email_mode=:schoolEmail,leave_email_mode=:leaveEmail,
                    quiet_hours_enabled=:quietEnabled,quiet_hours_start=:quietStart,
                    quiet_hours_end=:quietEnd,time_zone=:timeZone,
                    updated_at=:now, version=version+1
                WHERE organization_id=:organizationId AND user_id=:userId AND version=:version
                """, parameters);
        if (changed == 0 && request.version() == 0) {
            try {
                changed = jdbc.update("""
                        INSERT INTO notification_preferences(
                            organization_id,user_id,browser_notifications_enabled,sounds_enabled,
                            timetable_enabled,attendance_enabled,results_enabled,security_enabled,school_enabled,
                            approvals_enabled,leave_enabled,timetable_email_mode,attendance_email_mode,results_email_mode,
                            approvals_email_mode,school_email_mode,leave_email_mode,quiet_hours_enabled,quiet_hours_start,
                            quiet_hours_end,time_zone)
                        VALUES(:organizationId,:userId,:browser,:sounds,:timetable,:attendance,
                               :results,:security,:school,:approvals,:leave,:timetableEmail,:attendanceEmail,
                               :resultsEmail,:approvalsEmail,:schoolEmail,:leaveEmail,:quietEnabled,:quietStart,
                               :quietEnd,:timeZone)
                        """, parameters);
            } catch (DuplicateKeyException exception) {
                throw stalePreferences(userId);
            }
        }
        if (changed == 0) throw stalePreferences(userId);
        return preferences(organizationId, userId);
    }

    @Override
    @Transactional
    public IngestNotificationResponse ingest(String sourceService, IngestNotificationRequest request) {
        validateIngestion(sourceService, request);
        Set<Long> recipientIds = Set.copyOf(request.recipientUserIds());
        List<User> recipients = users.findAllByOrganizationIdAndIdIn(
                request.organizationId(), recipientIds);
        if (recipients.size() != recipientIds.size()) {
            throw new IllegalArgumentException("Every notification recipient must be an organization user");
        }

        UUID proposedEventId = UUID.randomUUID();
        OffsetDateTime occurredAt = request.occurredAt() == null ? now() : request.occurredAt();
        MapSqlParameterSource parameters = eventParameters(
                sourceService, request, proposedEventId, occurredAt);
        int inserted = jdbc.update("""
                INSERT INTO notification_events(
                    id,organization_id,source_service,source_event_id,event_type,category,importance,
                    heading,context,action_type,action_reference_id,action_student_user_id,
                    action_academic_year_id,grouping_key,occurred_at)
                VALUES(:eventId,:organizationId,:sourceService,:sourceEventId,:eventType,:category,
                       :importance,:heading,:context,:actionType,:referenceId,:studentUserId,
                       :academicYearId,:groupingKey,:occurredAt)
                ON CONFLICT(organization_id,source_service,source_event_id) DO NOTHING
                """, parameters);
        UUID eventId = inserted == 1 ? proposedEventId : jdbc.queryForObject("""
                SELECT id FROM notification_events
                WHERE organization_id=:organizationId
                  AND source_service=:sourceService AND source_event_id=:sourceEventId
                """, parameters, UUID.class);
        if (eventId == null) throw new IllegalStateException("Notification event could not be resolved");

        if (inserted == 0) {
            Integer matchingEvent = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM notification_events
                    WHERE id=:eventId AND organization_id=:organizationId
                      AND event_type=:eventType AND category=:category AND importance=:importance
                      AND heading=:heading AND context=:context AND action_type=:actionType
                      AND action_reference_id IS NOT DISTINCT FROM :referenceId
                      AND action_student_user_id IS NOT DISTINCT FROM :studentUserId
                      AND action_academic_year_id IS NOT DISTINCT FROM :academicYearId
                      AND grouping_key IS NOT DISTINCT FROM :groupingKey
                    """, parameters.addValue("eventId", eventId), Integer.class);
            List<Long> existingRecipients = jdbc.queryForList("""
                    SELECT user_id FROM notification_recipients
                    WHERE organization_id=:organizationId AND event_id=:eventId
                    """, parameters, Long.class);
            if (matchingEvent == null || matchingEvent != 1
                    || !Set.copyOf(existingRecipients).equals(recipientIds)) {
                throw new IllegalArgumentException(
                        "Source event ID is already associated with different notification data");
            }
        }

        Map<Long, DeliveryPreference> deliveryPreferences = deliveryPreferences(
                request.organizationId(), recipientIds);
        int recipientCount = 0;
        for (Long recipientId : recipientIds) {
            DeliveryPreference preference = deliveryPreferences.getOrDefault(recipientId,
                    DeliveryPreference.defaults());
            boolean inApp = mandatoryInApp(request) || preference.inApp(request);
            UUID recipientRowId = UUID.randomUUID();
            recipientCount += jdbc.update("""
                    INSERT INTO notification_recipients(id,organization_id,event_id,user_id,inbox_suppressed)
                    VALUES(:id,:organizationId,:eventId,:userId,:suppressed)
                    ON CONFLICT(event_id,user_id) DO NOTHING
                    """, new MapSqlParameterSource()
                    .addValue("id", recipientRowId).addValue("organizationId", request.organizationId())
                    .addValue("eventId", eventId).addValue("userId", recipientId)
                    .addValue("suppressed", !inApp));
            if (inserted == 1) enqueueEmail(request, eventId, recipientRowId, recipientId, preference);
        }
        return new IngestNotificationResponse(eventId, inserted == 1, recipientCount);
    }

    @Override
    @Transactional
    public ResolveNotificationGroupResponse resolveGroup(String sourceService, long organizationId,
                                                          String groupingKey, String excludeSourceEventId) {
        if (!Set.of("academic-service", "school-service", "identity-service")
                .contains(sourceService)) {
            throw new IllegalArgumentException("Notification source service is not allowed");
        }
        String normalized = normalize(groupingKey);
        if (normalized == null || normalized.length() > 160) {
            throw new IllegalArgumentException("Notification grouping key is invalid");
        }
        String excluded = normalize(excludeSourceEventId);
        if (excluded == null || excluded.length() > 160) {
            throw new IllegalArgumentException("Excluded source event ID is invalid");
        }
        OffsetDateTime resolvedAt = now();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("organizationId", organizationId)
                .addValue("sourceService", sourceService)
                .addValue("groupingKey", normalized)
                .addValue("excludeSourceEventId", excluded)
                .addValue("resolvedAt", resolvedAt);
        List<UUID> eventIds = jdbc.queryForList("""
                UPDATE notification_events
                   SET resolved_at=:resolvedAt
                 WHERE organization_id=:organizationId AND source_service=:sourceService
                   AND grouping_key=:groupingKey AND resolved_at IS NULL
                   AND source_event_id<>:excludeSourceEventId
                RETURNING id
                """, parameters, UUID.class);
        if (!eventIds.isEmpty()) {
            jdbc.update("""
                    UPDATE notification_recipients
                       SET read_at=COALESCE(read_at,:resolvedAt),
                           navbar_hidden_at=COALESCE(navbar_hidden_at,:resolvedAt),
                           updated_at=:resolvedAt, version=version+1
                     WHERE organization_id=:organizationId AND event_id IN (:eventIds)
                       AND deleted_at IS NULL
                    """, parameters.addValue("eventIds", eventIds));
            jdbc.update("""
                    UPDATE notification_email_outbox
                       SET status='CANCELLED',lease_owner=NULL,lease_until=NULL,updated_at=:resolvedAt
                     WHERE organization_id=:organizationId AND event_id IN (:eventIds)
                       AND status='PENDING'
                    """, parameters);
        }
        return new ResolveNotificationGroupResponse(eventIds.size());
    }

    private String filterConditions(NotificationFilter filter, NotificationCategory category,
                                    MapSqlParameterSource parameters) {
        StringBuilder conditions = new StringBuilder();
        if (filter == NotificationFilter.UNREAD) conditions.append(" AND r.read_at IS NULL");
        if (filter == NotificationFilter.IMPORTANT) {
            conditions.append(" AND e.importance IN ('IMPORTANT','URGENT')");
        }
        if (category != null) {
            conditions.append(" AND e.category=:category");
            parameters.addValue("category", category.name());
        }
        return conditions.toString();
    }

    private NotificationItemResponse mapItem(ResultSet rs, int row) throws SQLException {
        return new NotificationItemResponse(
                rs.getObject("id", UUID.class), rs.getString("event_type"),
                NotificationCategory.valueOf(rs.getString("category")),
                NotificationImportance.valueOf(rs.getString("importance")),
                rs.getString("heading"), rs.getString("context"),
                new NotificationAction(NotificationActionType.valueOf(rs.getString("action_type")),
                        nullableLong(rs, "action_reference_id"),
                        nullableLong(rs, "action_student_user_id"),
                        nullableLong(rs, "action_academic_year_id")),
                rs.getString("grouping_key"), rs.getObject("occurred_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("read_at", OffsetDateTime.class),
                rs.getObject("navbar_hidden_at") != null);
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private void requireMutable(long organizationId, long userId, UUID notificationId) {
        if (jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification_recipients
                WHERE organization_id=:organizationId AND user_id=:userId
                  AND id=:notificationId AND deleted_at IS NULL
                """, scopedId(organizationId, userId, notificationId), Long.class) != 1L) {
            throw new NotificationNotFoundException();
        }
    }

    private void validateIngestion(String sourceService, IngestNotificationRequest request) {
        Map<String, Set<NotificationEventType>> allowed = Map.of(
                "academic-service", ACADEMIC_EVENTS,
                "school-service", SCHOOL_EVENTS,
                "identity-service", IDENTITY_EVENTS,
                "development-fixture", EnumSet.of(NotificationEventType.DEVELOPMENT_SAMPLE));
        if (!allowed.getOrDefault(sourceService, Set.of()).contains(request.eventType())) {
            throw new IllegalArgumentException("Event type is not allowed for the authenticated source service");
        }
        rejectMarkup(request.heading(), "heading");
        rejectMarkup(request.context(), "context");
        NotificationAction action = request.action() == null
                ? new NotificationAction(NotificationActionType.NONE, null, null, null)
                : request.action();
        if (action.type() == NotificationActionType.NONE
                && (action.referenceId() != null || action.studentUserId() != null
                || action.academicYearId() != null)) {
            throw new IllegalArgumentException("NONE actions cannot contain action identifiers");
        }
        validateTypedAction(request.eventType(), request.category(), action.type());
    }

    private void validateTypedAction(NotificationEventType eventType,
                                     NotificationCategory category,
                                     NotificationActionType actionType) {
        boolean valid = switch (eventType) {
            case COVER_NEEDED, APPROVAL_REQUIRED, CONFIRMED_SUBSTITUTION,
                    IMMINENT_UNCOVERED, CLASS_UNCOVERED, COVER_CHANGED ->
                    category == NotificationCategory.TIMETABLE
                            && actionType == NotificationActionType.DAILY_COVERAGE;
            case TIMETABLE_PUBLISHED, TIMETABLE_CHANGED,
                    TIMETABLE_EXPIRING, TIMETABLE_EXPIRES_TODAY ->
                    category == NotificationCategory.TIMETABLE
                            && actionType == NotificationActionType.TIMETABLE;
            case EXAM_SCHEDULE_PUBLISHED -> category == NotificationCategory.SCHOOL
                    && actionType == NotificationActionType.EXAMINATION;
            case ATTENDANCE_RECORDED, ATTENDANCE_CORRECTED ->
                    category == NotificationCategory.ATTENDANCE
                            && actionType == NotificationActionType.ATTENDANCE;
            case RESULT_PUBLISHED -> category == NotificationCategory.RESULTS
                    && actionType == NotificationActionType.PUBLISHED_RESULT;
            case REPORT_CARD_PUBLISHED -> category == NotificationCategory.RESULTS
                    && actionType == NotificationActionType.REPORT_CARD;
            case SECURITY_ALERT -> category == NotificationCategory.SECURITY
                    && actionType == NotificationActionType.ACCOUNT_SECURITY;
            case ACCOUNT_LOCKED, ACCOUNT_STATUS_CHANGED -> category == NotificationCategory.SECURITY
                    && actionType == NotificationActionType.ACCOUNT_SECURITY;
            case ROLE_ASSIGNMENT_REQUESTED, ROLE_ASSIGNMENT_APPROVAL_REQUIRED,
                    ROLE_ASSIGNMENT_APPROVED, ROLE_ASSIGNMENT_REJECTED, ROLE_ASSIGNMENT_CANCELLED,
                    ROLE_REMOVAL_REQUESTED, ROLE_REMOVAL_APPROVAL_REQUIRED,
                    ROLE_REMOVAL_APPROVED, ROLE_REMOVAL_REJECTED, ROLE_REMOVAL_CANCELLED ->
                    category == NotificationCategory.APPROVALS
                            && actionType == NotificationActionType.ROLE_REQUEST;
            case SCHOOL_ANNOUNCEMENT -> category == NotificationCategory.SCHOOL
                    && actionType == NotificationActionType.ANNOUNCEMENT;
            case DAWNRISE_RELEASE -> category == NotificationCategory.SCHOOL
                    && actionType == NotificationActionType.RELEASE_DETAILS;
            case LEAVE_REQUESTED, LEAVE_APPROVAL_REQUIRED, LEAVE_APPROVED,
                    LEAVE_REJECTED, LEAVE_CANCELLED -> category == NotificationCategory.LEAVE
                    && actionType == NotificationActionType.LEAVE_REQUEST;
            case SCHOOL_PROVISIONING_COMPLETED, SCHOOL_PROVISIONING_FAILED ->
                    category == NotificationCategory.SCHOOL
                            && (actionType == NotificationActionType.NONE
                            || actionType == NotificationActionType.SETTINGS);
            case DEVELOPMENT_SAMPLE -> category == NotificationCategory.GENERAL
                    && (actionType == NotificationActionType.NONE
                    || actionType == NotificationActionType.SETTINGS);
        };
        if (!valid) throw new IllegalArgumentException(
                "Notification category and action do not match the event type");
    }

    private void rejectMarkup(String value, String field) {
        if (value.contains("<") || value.contains(">")) {
            throw new IllegalArgumentException("Notification " + field + " must be plain text");
        }
    }

    private MapSqlParameterSource eventParameters(String sourceService,
            IngestNotificationRequest request, UUID eventId, OffsetDateTime occurredAt) {
        NotificationAction action = request.action() == null
                ? new NotificationAction(NotificationActionType.NONE, null, null, null)
                : request.action();
        return new MapSqlParameterSource()
                .addValue("eventId", eventId).addValue("organizationId", request.organizationId())
                .addValue("sourceService", sourceService).addValue("sourceEventId", request.sourceEventId().trim())
                .addValue("eventType", request.eventType().name()).addValue("category", request.category().name())
                .addValue("importance", request.importance().name()).addValue("heading", request.heading().trim())
                .addValue("context", request.context().trim()).addValue("actionType", action.type().name())
                .addValue("referenceId", action.referenceId()).addValue("studentUserId", action.studentUserId())
                .addValue("academicYearId", action.academicYearId())
                .addValue("groupingKey", normalize(request.groupingKey())).addValue("occurredAt", occurredAt);
    }

    private MapSqlParameterSource preferenceParameters(long organizationId, long userId,
                                                        UpdateNotificationPreferencesRequest request) {
        if (request.quietHoursEnabled()
                && (request.quietHoursStart() == null || request.quietHoursEnd() == null
                || request.quietHoursStart().equals(request.quietHoursEnd()))) {
            throw new IllegalArgumentException("Quiet hours require different start and end times");
        }
        return scope(organizationId, userId).addValue("browser", request.browserNotificationsEnabled())
                .addValue("sounds", request.soundsEnabled()).addValue("timetable", request.timetableEnabled())
                .addValue("attendance", request.attendanceEnabled()).addValue("results", request.resultsEnabled())
                .addValue("security", request.securityEnabled()).addValue("school", request.schoolEnabled())
                .addValue("approvals", request.approvalsEnabled()).addValue("leave", request.leaveEnabled())
                .addValue("timetableEmail", request.timetableEmailMode().name())
                .addValue("attendanceEmail", request.attendanceEmailMode().name())
                .addValue("resultsEmail", request.resultsEmailMode().name())
                .addValue("approvalsEmail", request.approvalsEmailMode().name())
                .addValue("schoolEmail", request.schoolEmailMode().name())
                .addValue("leaveEmail", request.leaveEmailMode().name())
                .addValue("quietEnabled", request.quietHoursEnabled())
                .addValue("quietStart", request.quietHoursEnabled() ? request.quietHoursStart() : null)
                .addValue("quietEnd", request.quietHoursEnabled() ? request.quietHoursEnd() : null)
                .addValue("timeZone", validateZone(request.timeZone()))
                .addValue("version", request.version()).addValue("now", now());
    }

    private NotificationPreferencesResponse mapPreferences(ResultSet rs) throws SQLException {
        return new NotificationPreferencesResponse(rs.getBoolean("browser_notifications_enabled"),
                rs.getBoolean("sounds_enabled"), rs.getBoolean("timetable_enabled"),
                rs.getBoolean("attendance_enabled"), rs.getBoolean("results_enabled"),
                true, rs.getBoolean("school_enabled"), rs.getBoolean("approvals_enabled"),
                rs.getBoolean("leave_enabled"),
                NotificationEmailMode.valueOf(rs.getString("timetable_email_mode")),
                NotificationEmailMode.valueOf(rs.getString("attendance_email_mode")),
                NotificationEmailMode.valueOf(rs.getString("results_email_mode")),
                NotificationEmailMode.valueOf(rs.getString("approvals_email_mode")),
                NotificationEmailMode.valueOf(rs.getString("school_email_mode")),
                NotificationEmailMode.valueOf(rs.getString("leave_email_mode")),
                rs.getBoolean("quiet_hours_enabled"), rs.getObject("quiet_hours_start", LocalTime.class),
                rs.getObject("quiet_hours_end", LocalTime.class), rs.getString("time_zone"),
                Set.of("SECURITY", "URGENT_OPERATIONAL"),
                Set.of("PASSWORD_CHANGED", "ACCOUNT_LOCKED", "ACCOUNT_SUSPENDED",
                        "URGENT_UNCOVERED_CLASS"), rs.getLong("version"));
    }

    private NotificationPreferencesResponse defaults() {
        return new NotificationPreferencesResponse(false, false, true, true, true, true, true, true, true,
                NotificationEmailMode.IMMEDIATE, NotificationEmailMode.IMMEDIATE,
                NotificationEmailMode.IMMEDIATE, NotificationEmailMode.IMMEDIATE,
                NotificationEmailMode.IMMEDIATE, NotificationEmailMode.IMMEDIATE, false, null, null, "UTC",
                Set.of("SECURITY", "URGENT_OPERATIONAL"),
                Set.of("PASSWORD_CHANGED", "ACCOUNT_LOCKED", "ACCOUNT_SUSPENDED",
                        "URGENT_UNCOVERED_CLASS"), 0);
    }

    private Map<Long, DeliveryPreference> deliveryPreferences(long organizationId, Set<Long> userIds) {
        Map<Long, DeliveryPreference> values = new HashMap<>();
        jdbc.query("""
                SELECT u.id,u.email,p.timetable_enabled,p.attendance_enabled,p.results_enabled,
                       p.school_enabled,p.approvals_enabled,p.leave_enabled,p.timetable_email_mode,p.attendance_email_mode,
                       p.results_email_mode,p.school_email_mode,p.approvals_email_mode,p.leave_email_mode,p.quiet_hours_enabled,
                       p.quiet_hours_start,p.quiet_hours_end,COALESCE(p.time_zone,'UTC') time_zone
                  FROM users u LEFT JOIN notification_preferences p
                    ON p.organization_id=u.organization_id AND p.user_id=u.id
                 WHERE u.organization_id=:organizationId AND u.id IN (:userIds)
                """, new MapSqlParameterSource("organizationId", organizationId).addValue("userIds", userIds),
                (org.springframework.jdbc.core.RowCallbackHandler)
                        rs -> values.put(rs.getLong("id"), DeliveryPreference.from(rs)));
        return values;
    }

    private boolean mandatoryInApp(IngestNotificationRequest request) {
        return request.category() == NotificationCategory.SECURITY
                || Set.of(NotificationEventType.IMMINENT_UNCOVERED,
                NotificationEventType.CLASS_UNCOVERED).contains(request.eventType());
    }

    private void enqueueEmail(IngestNotificationRequest request, UUID eventId, UUID recipientId,
                              long userId, DeliveryPreference preference) {
        NotificationEmailMode mode = preference.emailMode(request);
        boolean mandatory = request.category() == NotificationCategory.SECURITY
                || Set.of(NotificationEventType.IMMINENT_UNCOVERED,
                NotificationEventType.CLASS_UNCOVERED).contains(request.eventType());
        if (mandatory) mode = NotificationEmailMode.IMMEDIATE;
        if (mode == NotificationEmailMode.DISABLED || preference.email() == null
                || preference.email().isBlank()) return;
        OffsetDateTime availableAt = preference.availableAt(now(), mode, mandatory);
        NotificationAction action = request.action() == null
                ? new NotificationAction(NotificationActionType.NONE, null, null, null) : request.action();
        String variables = "{\"heading\":\"" + json(request.heading()) + "\",\"context\":\""
                + json(request.context()) + "\"}";
        jdbc.update("""
                INSERT INTO notification_email_outbox(
                    id,organization_id,event_id,recipient_id,user_id,destination_email,template_key,
                    subject,template_variables,action_type,action_reference_id,delivery_mode,
                    message_id,available_at)
                VALUES(:id,:org,:event,:recipient,:user,:email,:template,:subject,CAST(:variables AS JSONB),
                       :action,:reference,:mode,:messageId,:availableAt)
                ON CONFLICT(event_id,user_id) DO NOTHING
                """, new MapSqlParameterSource().addValue("id", UUID.randomUUID())
                .addValue("org", request.organizationId()).addValue("event", eventId)
                .addValue("recipient", recipientId).addValue("user", userId)
                .addValue("email", preference.email()).addValue("template", request.eventType().name())
                .addValue("subject", safeSubject(request)).addValue("variables", variables)
                .addValue("action", action.type().name()).addValue("reference", action.referenceId())
                .addValue("mode", mode.name()).addValue("messageId", eventId + "." + userId + "@dawnrise")
                .addValue("availableAt", availableAt));
    }

    private String safeSubject(IngestNotificationRequest request) {
        return switch (request.category()) {
            case SECURITY -> "Dawnrise account security update";
            case ATTENDANCE -> "Dawnrise attendance update";
            case RESULTS -> "A new academic update is available in Dawnrise";
            case TIMETABLE -> request.importance() == NotificationImportance.URGENT
                    ? "Urgent Dawnrise timetable alert" : "Dawnrise timetable update";
            case SCHOOL -> "Dawnrise school update";
            default -> "New Dawnrise notification";
        };
    }

    private String validateZone(String value) {
        try { return ZoneId.of(value.trim()).getId(); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Notification timezone is invalid"); }
    }

    private String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }

    private record DeliveryPreference(String email, boolean timetable, boolean attendance,
                                      boolean results, boolean school, boolean approvals, boolean leave,
                                      NotificationEmailMode timetableEmail, NotificationEmailMode attendanceEmail,
                                      NotificationEmailMode resultsEmail, NotificationEmailMode schoolEmail,
                                      NotificationEmailMode approvalsEmail, NotificationEmailMode leaveEmail, boolean quietEnabled,
                                      LocalTime quietStart, LocalTime quietEnd, ZoneId zone) {
        static DeliveryPreference defaults() { return new DeliveryPreference(null,true,true,true,true,true,true,
                NotificationEmailMode.IMMEDIATE,NotificationEmailMode.IMMEDIATE,NotificationEmailMode.IMMEDIATE,
                NotificationEmailMode.IMMEDIATE,NotificationEmailMode.IMMEDIATE,NotificationEmailMode.IMMEDIATE,false,null,null,ZoneOffset.UTC); }
        static DeliveryPreference from(ResultSet rs) throws SQLException {
            boolean absent = rs.getObject("timetable_enabled") == null;
            return new DeliveryPreference(rs.getString("email"), absent || rs.getBoolean("timetable_enabled"),
                    absent || rs.getBoolean("attendance_enabled"), absent || rs.getBoolean("results_enabled"),
                    absent || rs.getBoolean("school_enabled"), absent || rs.getBoolean("approvals_enabled"),
                    absent || rs.getBoolean("leave_enabled"),
                    mode(rs,"timetable_email_mode"),mode(rs,"attendance_email_mode"),mode(rs,"results_email_mode"),
                    mode(rs,"school_email_mode"),mode(rs,"approvals_email_mode"),mode(rs,"leave_email_mode"),
                    !absent && rs.getBoolean("quiet_hours_enabled"),rs.getObject("quiet_hours_start",LocalTime.class),
                    rs.getObject("quiet_hours_end",LocalTime.class),ZoneId.of(rs.getString("time_zone")));
        }
        static NotificationEmailMode mode(ResultSet rs,String column) throws SQLException {
            String value=rs.getString(column); return value==null?NotificationEmailMode.IMMEDIATE:NotificationEmailMode.valueOf(value);
        }
        boolean inApp(IngestNotificationRequest request) { return switch(request.category()) {
            case TIMETABLE -> request.eventType()==NotificationEventType.APPROVAL_REQUIRED?approvals:timetable;
            case ATTENDANCE -> attendance; case RESULTS -> results; case SCHOOL -> school;
            case APPROVALS -> approvals; case LEAVE -> leave;
            case SECURITY -> true; default -> true; }; }
        NotificationEmailMode emailMode(IngestNotificationRequest request) { return switch(request.category()) {
            case TIMETABLE -> request.eventType()==NotificationEventType.APPROVAL_REQUIRED?approvalsEmail:timetableEmail;
            case ATTENDANCE -> attendanceEmail; case RESULTS -> resultsEmail; case SCHOOL -> schoolEmail;
            case APPROVALS -> approvalsEmail; case LEAVE -> leaveEmail;
            case SECURITY -> NotificationEmailMode.IMMEDIATE; default -> NotificationEmailMode.DISABLED; }; }
        OffsetDateTime availableAt(OffsetDateTime now,NotificationEmailMode mode,boolean bypass) {
            ZonedDateTime local=now.atZoneSameInstant(zone);
            if(mode==NotificationEmailMode.DIGEST) return local.plusHours(1).withMinute(0).withSecond(0).withNano(0).toOffsetDateTime();
            if(bypass||!quietEnabled) return now;
            LocalTime time=local.toLocalTime(); boolean inside=quietStart.isBefore(quietEnd)
                    ? !time.isBefore(quietStart)&&time.isBefore(quietEnd)
                    : !time.isBefore(quietStart)||time.isBefore(quietEnd);
            if(!inside) return now;
            ZonedDateTime end=local.with(quietEnd); if(!end.isAfter(local)) end=end.plusDays(1);
            return end.toOffsetDateTime();
        }
    }

    private ObjectOptimisticLockingFailureException stalePreferences(long userId) {
        return new ObjectOptimisticLockingFailureException("NotificationPreferences", userId);
    }

    private MapSqlParameterSource scope(long organizationId, long userId) {
        return new MapSqlParameterSource().addValue("organizationId", organizationId).addValue("userId", userId);
    }

    private MapSqlParameterSource scopedId(long organizationId, long userId, UUID notificationId) {
        return scope(organizationId, userId).addValue("notificationId", notificationId);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
