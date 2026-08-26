INSERT INTO platform_permissions (
    code,
    description,
    owning_service,
    sensitive
)
VALUES
    (
        'ORGANIZATION_UPDATE',
        'Update provisioned organization information',
        'school-service',
        TRUE
    ),
    (
        'ORGANIZATION_STATUS_MANAGE',
        'Deactivate or restore provisioned organizations',
        'school-service',
        TRUE
    );

-- Super Admin receives both new permissions.
INSERT INTO platform_role_permissions (
    role,
    permission_id
)
SELECT
    'PLATFORM_SUPER_ADMIN',
    id
FROM platform_permissions
WHERE code IN (
               'ORGANIZATION_UPDATE',
               'ORGANIZATION_STATUS_MANAGE'
    );

-- Onboarding specialists can correct organization information
-- during onboarding, but cannot deactivate or restore organizations.
INSERT INTO platform_role_permissions (
    role,
    permission_id
)
SELECT
    'ONBOARDING_SPECIALIST',
    id
FROM platform_permissions
WHERE code = 'ORGANIZATION_UPDATE';

-- Onboarding managers can update information and manage status.
INSERT INTO platform_role_permissions (
    role,
    permission_id
)
SELECT
    'ONBOARDING_MANAGER',
    id
FROM platform_permissions
WHERE code IN (
               'ORGANIZATION_UPDATE',
               'ORGANIZATION_STATUS_MANAGE'
    );