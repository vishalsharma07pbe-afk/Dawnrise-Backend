CREATE TABLE notification_events (
    id UUID PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    source_service VARCHAR(40) NOT NULL,
    source_event_id VARCHAR(160) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    category VARCHAR(30) NOT NULL,
    importance VARCHAR(20) NOT NULL,
    heading VARCHAR(160) NOT NULL,
    context VARCHAR(500) NOT NULL,
    action_type VARCHAR(40) NOT NULL DEFAULT 'NONE',
    action_reference_id BIGINT,
    action_student_user_id BIGINT,
    action_academic_year_id BIGINT,
    grouping_key VARCHAR(160),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_notification_events_source
        UNIQUE (organization_id, source_service, source_event_id),
    CONSTRAINT uk_notification_events_id_organization
        UNIQUE (id, organization_id),
    CONSTRAINT fk_notification_events_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    CONSTRAINT chk_notification_events_source
        CHECK (source_service IN ('identity-service', 'academic-service', 'school-service', 'development-fixture')),
    CONSTRAINT chk_notification_events_type
        CHECK (event_type IN (
            'DEVELOPMENT_SAMPLE', 'COVER_NEEDED', 'APPROVAL_REQUIRED',
            'CONFIRMED_SUBSTITUTION', 'IMMINENT_UNCOVERED', 'CLASS_UNCOVERED',
            'COVER_CHANGED', 'ATTENDANCE_RECORDED', 'ATTENDANCE_CORRECTED',
            'RESULT_PUBLISHED', 'REPORT_CARD_PUBLISHED', 'SECURITY_ALERT',
            'SCHOOL_PROVISIONING_COMPLETED', 'SCHOOL_PROVISIONING_FAILED'
        )),
    CONSTRAINT chk_notification_events_category
        CHECK (category IN ('GENERAL', 'TIMETABLE', 'ATTENDANCE', 'RESULTS', 'SECURITY', 'SCHOOL')),
    CONSTRAINT chk_notification_events_importance
        CHECK (importance IN ('NORMAL', 'IMPORTANT', 'URGENT')),
    CONSTRAINT chk_notification_events_action
        CHECK (action_type IN (
            'NONE', 'DAILY_COVERAGE', 'ATTENDANCE', 'PUBLISHED_RESULT',
            'REPORT_CARD', 'ACCOUNT_SECURITY', 'SETTINGS'
        )),
    CONSTRAINT chk_notification_events_text
        CHECK (
            BTRIM(source_event_id) <> '' AND BTRIM(heading) <> '' AND BTRIM(context) <> ''
            AND heading !~ '[<>]' AND context !~ '[<>]'
            AND (grouping_key IS NULL OR BTRIM(grouping_key) <> '')
        ),
    CONSTRAINT chk_notification_events_action_reference
        CHECK (
            (action_type = 'NONE' AND action_reference_id IS NULL
                AND action_student_user_id IS NULL AND action_academic_year_id IS NULL)
            OR action_type <> 'NONE'
        ),
    CONSTRAINT chk_notification_events_positive_action_ids
        CHECK (
            (action_reference_id IS NULL OR action_reference_id > 0)
            AND (action_student_user_id IS NULL OR action_student_user_id > 0)
            AND (action_academic_year_id IS NULL OR action_academic_year_id > 0)
        )
);

CREATE TABLE notification_recipients (
    id UUID PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    event_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    navbar_hidden_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    delete_undo_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_notification_recipients_event_user UNIQUE (event_id, user_id),
    CONSTRAINT fk_notification_recipients_event
        FOREIGN KEY (event_id, organization_id)
            REFERENCES notification_events(id, organization_id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_recipients_user
        FOREIGN KEY (user_id, organization_id)
            REFERENCES users(id, organization_id) ON DELETE CASCADE,
    CONSTRAINT chk_notification_recipients_delete
        CHECK (
            (deleted_at IS NULL AND delete_undo_until IS NULL)
            OR (deleted_at IS NOT NULL AND delete_undo_until IS NOT NULL
                AND delete_undo_until >= deleted_at)
        ),
    CONSTRAINT chk_notification_recipients_version CHECK (version >= 0)
);

CREATE TABLE notification_preferences (
    organization_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    browser_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    sounds_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    timetable_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    attendance_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    results_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    security_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    school_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_notification_preferences PRIMARY KEY (organization_id, user_id),
    CONSTRAINT fk_notification_preferences_user
        FOREIGN KEY (user_id, organization_id)
            REFERENCES users(id, organization_id) ON DELETE CASCADE,
    CONSTRAINT chk_notification_preferences_version CHECK (version >= 0)
);

CREATE INDEX idx_notification_recipients_inbox
    ON notification_recipients (organization_id, user_id, created_at DESC, id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_notification_recipients_unread
    ON notification_recipients (organization_id, user_id, created_at DESC)
    WHERE read_at IS NULL AND deleted_at IS NULL;

CREATE INDEX idx_notification_recipients_navbar
    ON notification_recipients (organization_id, user_id, created_at DESC)
    WHERE navbar_hidden_at IS NULL AND deleted_at IS NULL;

CREATE INDEX idx_notification_events_grouping
    ON notification_events (organization_id, grouping_key, occurred_at DESC)
    WHERE grouping_key IS NOT NULL;

