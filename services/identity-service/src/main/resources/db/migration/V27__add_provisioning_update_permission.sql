INSERT INTO platform_permissions (
    code,
    description,
    owning_service,
    sensitive
)
VALUES (
    'PROVISIONING_UPDATE',
    'Correct failed organization provisioning request data',
    'school-service',
    TRUE
);

INSERT INTO platform_role_permissions (
    role,
    permission_id
)
SELECT role_mapping.role, platform_permissions.id
FROM (
    VALUES
    ('PLATFORM_SUPER_ADMIN'),
    ('ONBOARDING_SPECIALIST'),
    ('ONBOARDING_MANAGER')
) AS role_mapping(role)
JOIN platform_permissions
    ON platform_permissions.code = 'PROVISIONING_UPDATE';

INSERT INTO platform_role_permissions (
    role,
    permission_id
)
SELECT
    'ONBOARDING_MANAGER',
    platform_permissions.id
FROM platform_permissions
WHERE platform_permissions.code = 'ORGANIZATION_CREATE'
  AND NOT EXISTS (
      SELECT 1
      FROM platform_role_permissions existing
      JOIN platform_permissions existing_permission
          ON existing_permission.id = existing.permission_id
      WHERE existing.role = 'ONBOARDING_MANAGER'
        AND existing_permission.code = 'ORGANIZATION_CREATE'
  );
