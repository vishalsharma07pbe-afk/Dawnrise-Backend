-- Email remains optional.
-- When supplied, it must be unique case-insensitively within an organization.

CREATE UNIQUE INDEX uk_users_organization_email_ci
    ON users (organization_id, LOWER(email))
    WHERE email IS NOT NULL
        AND BTRIM(email) <> '';