ALTER TABLE school_announcements
    ADD COLUMN delivery_channels JSONB NOT NULL DEFAULT '["APP"]'::JSONB,
    ADD COLUMN voice_script VARCHAR(2000);
ALTER TABLE school_announcements
    ADD CONSTRAINT chk_communication_delivery_channels CHECK (
        JSONB_TYPEOF(delivery_channels)='array' AND JSONB_ARRAY_LENGTH(delivery_channels)>0),
    ADD CONSTRAINT chk_communication_voice_script CHECK (
        voice_script IS NULL OR (BTRIM(voice_script)<>'' AND voice_script !~ '[<>]'));

ALTER TABLE school_notification_outbox
    ADD COLUMN delivery_channels JSONB NOT NULL DEFAULT '["APP"]'::JSONB;
ALTER TABLE school_notification_outbox
    ADD CONSTRAINT chk_school_outbox_delivery_channels CHECK (
        JSONB_TYPEOF(delivery_channels)='array' AND JSONB_ARRAY_LENGTH(delivery_channels)>0);
