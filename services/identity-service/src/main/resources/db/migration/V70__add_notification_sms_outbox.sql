ALTER TABLE notification_preferences
    ADD COLUMN school_sms_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE notification_sms_outbox (
    id UUID PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    event_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    destination_phone VARCHAR(20) NOT NULL CHECK (destination_phone ~ '^\\+[1-9][0-9]{7,14}$'),
    template_key VARCHAR(60) NOT NULL CHECK (BTRIM(template_key)<>''),
    template_variables JSONB NOT NULL CHECK (JSONB_TYPEOF(template_variables)='object'),
    provider_message_id VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','ACCEPTED','DELIVERED','FAILED','DEAD_LETTER','CANCELLED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count>=0),
    available_at TIMESTAMPTZ NOT NULL,
    lease_owner UUID,
    lease_until TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    dead_lettered_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_sms_event_user UNIQUE(event_id,user_id),
    CONSTRAINT fk_notification_sms_event FOREIGN KEY(event_id,organization_id)
        REFERENCES notification_events(id,organization_id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_sms_recipient FOREIGN KEY(recipient_id)
        REFERENCES notification_recipients(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_sms_user FOREIGN KEY(user_id,organization_id)
        REFERENCES users(id,organization_id) ON DELETE CASCADE,
    CONSTRAINT chk_notification_sms_lease CHECK ((lease_owner IS NULL)=(lease_until IS NULL))
);

CREATE INDEX idx_notification_sms_claim ON notification_sms_outbox(status,available_at,lease_until,id)
    WHERE status='PENDING';
CREATE INDEX idx_notification_sms_provider_message ON notification_sms_outbox(provider_message_id)
    WHERE provider_message_id IS NOT NULL;
