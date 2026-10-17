CREATE TABLE authenticated_sessions (
    id UUID PRIMARY KEY,
    identity_type VARCHAR(32) NOT NULL,
    user_id BIGINT,
    platform_user_id BIGINT,
    authenticated_at TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_authenticated_sessions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_authenticated_sessions_platform_user
        FOREIGN KEY (platform_user_id) REFERENCES platform_users(id) ON DELETE CASCADE,
    CONSTRAINT chk_authenticated_sessions_owner
        CHECK (
            (identity_type = 'ORGANIZATION_USER' AND user_id IS NOT NULL AND platform_user_id IS NULL)
            OR
            (identity_type = 'PLATFORM_USER' AND user_id IS NULL AND platform_user_id IS NOT NULL)
        ),
    CONSTRAINT chk_authenticated_sessions_positive_owner
        CHECK (COALESCE(user_id, platform_user_id) > 0),
    CONSTRAINT chk_authenticated_sessions_nonnegative_version
        CHECK (version >= 0),
    CONSTRAINT chk_authenticated_sessions_activity_after_authentication
        CHECK (last_activity_at >= authenticated_at),
    CONSTRAINT chk_authenticated_sessions_lock_after_authentication
        CHECK (locked_at IS NULL OR locked_at >= authenticated_at),
    CONSTRAINT chk_authenticated_sessions_retirement_after_authentication
        CHECK (retired_at IS NULL OR retired_at >= authenticated_at)
);

CREATE INDEX idx_authenticated_sessions_user_id
    ON authenticated_sessions(user_id);
CREATE INDEX idx_authenticated_sessions_platform_user_id
    ON authenticated_sessions(platform_user_id);

INSERT INTO authenticated_sessions (
    id,
    identity_type,
    user_id,
    authenticated_at,
    last_activity_at,
    locked_at,
    version
)
SELECT
    id,
    'ORGANIZATION_USER',
    user_id,
    authenticated_at,
    GREATEST(last_activity_at, authenticated_at),
    CASE
        WHEN locked_at IS NULL THEN NULL
        ELSE GREATEST(locked_at, authenticated_at)
    END,
    version
FROM parent_sessions
ON CONFLICT (id) DO NOTHING;

INSERT INTO authenticated_sessions (
    id,
    identity_type,
    platform_user_id,
    authenticated_at,
    last_activity_at,
    version
)
SELECT
    refresh_token.token_family_id,
    'PLATFORM_USER',
    refresh_token.platform_user_id,
    MIN(family_token.created_at),
    CURRENT_TIMESTAMP,
    0
FROM platform_refresh_tokens refresh_token
JOIN platform_refresh_tokens family_token
  ON family_token.token_family_id = refresh_token.token_family_id
JOIN platform_users account ON account.id = refresh_token.platform_user_id
WHERE refresh_token.revoked_at IS NULL
  AND refresh_token.expires_at > CURRENT_TIMESTAMP
  AND refresh_token.family_expires_at > CURRENT_TIMESTAMP
  AND account.status = 'ACTIVE'
GROUP BY refresh_token.token_family_id, refresh_token.platform_user_id
ON CONFLICT (id) DO NOTHING;

INSERT INTO authenticated_sessions (
    id,
    identity_type,
    user_id,
    authenticated_at,
    last_activity_at,
    version
)
SELECT
    refresh_token.token_family_id,
    'ORGANIZATION_USER',
    refresh_token.user_id,
    MIN(family_token.created_at),
    CURRENT_TIMESTAMP,
    0
FROM refresh_tokens refresh_token
JOIN refresh_tokens family_token
  ON family_token.token_family_id = refresh_token.token_family_id
JOIN users account ON account.id = refresh_token.user_id
WHERE refresh_token.revoked_at IS NULL
  AND refresh_token.expires_at > CURRENT_TIMESTAMP
  AND refresh_token.family_expires_at > CURRENT_TIMESTAMP
  AND account.status = 'ACTIVE'
GROUP BY refresh_token.token_family_id, refresh_token.user_id
ON CONFLICT (id) DO NOTHING;
