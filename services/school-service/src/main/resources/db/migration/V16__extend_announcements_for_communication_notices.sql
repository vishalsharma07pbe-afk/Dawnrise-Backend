ALTER TABLE school_announcement_audiences
    DROP CONSTRAINT school_announcement_audiences_audience_type_check;
ALTER TABLE school_announcement_audiences
    ADD CONSTRAINT school_announcement_audiences_audience_type_check CHECK (audience_type IN
        ('WHOLE_SCHOOL','ROLE','WING','GRADE','SECTION','STAFF','STUDENT','ACTIVE_GUARDIAN',
         'INDIVIDUAL','SECTION_FAMILIES','GRADE_FAMILIES','WING_FAMILIES'));
ALTER TABLE school_announcement_audiences
    DROP CONSTRAINT chk_school_announcement_audience_key;
ALTER TABLE school_announcement_audiences
    ADD CONSTRAINT chk_school_announcement_audience_key CHECK (
        (audience_type IN ('WHOLE_SCHOOL','STAFF','STUDENT','ACTIVE_GUARDIAN') AND audience_key IS NULL)
        OR (audience_type IN ('ROLE','WING','GRADE','SECTION','INDIVIDUAL',
                              'SECTION_FAMILIES','GRADE_FAMILIES','WING_FAMILIES')
            AND BTRIM(audience_key) <> ''));

ALTER TABLE school_announcements
    ADD COLUMN communication_notice BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN idempotency_key VARCHAR(120),
    ADD COLUMN request_fingerprint VARCHAR(64);
ALTER TABLE school_announcements
    ADD CONSTRAINT chk_communication_idempotency CHECK (
        (communication_notice AND idempotency_key IS NOT NULL AND request_fingerprint IS NOT NULL)
        OR (NOT communication_notice AND idempotency_key IS NULL AND request_fingerprint IS NULL));
CREATE UNIQUE INDEX uk_communication_notice_create_idempotency
    ON school_announcements(organization_id, created_by_user_id, idempotency_key)
    WHERE communication_notice;

CREATE TABLE communication_notice_mutations (
    organization_id BIGINT NOT NULL,
    announcement_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL CHECK (actor_user_id > 0),
    action VARCHAR(16) NOT NULL CHECK (action IN ('PUBLISH','SCHEDULE','CANCEL')),
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_communication_notice_mutation PRIMARY KEY
        (organization_id, actor_user_id, action, idempotency_key),
    CONSTRAINT fk_communication_notice_mutation FOREIGN KEY(announcement_id, organization_id)
        REFERENCES school_announcements(id, organization_id) ON DELETE CASCADE
);

ALTER TABLE school_notification_outbox
    ADD COLUMN sender_user_id BIGINT,
    ADD COLUMN school_wide_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN accepted_recipient_count INTEGER CHECK (accepted_recipient_count >= 0),
    ADD COLUMN recipient_set_accepted_at TIMESTAMPTZ;
ALTER TABLE school_notification_outbox
    ADD CONSTRAINT chk_communication_outbox_sender CHECK (
        source_type <> 'ANNOUNCEMENT' OR sender_user_id IS NULL OR sender_user_id > 0);
