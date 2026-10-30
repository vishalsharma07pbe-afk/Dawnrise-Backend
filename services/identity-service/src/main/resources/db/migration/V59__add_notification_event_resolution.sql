ALTER TABLE notification_events
    ADD COLUMN resolved_at TIMESTAMPTZ;

CREATE INDEX idx_notification_events_unresolved_group
    ON notification_events(organization_id,source_service,grouping_key,occurred_at DESC)
    WHERE grouping_key IS NOT NULL AND resolved_at IS NULL;
