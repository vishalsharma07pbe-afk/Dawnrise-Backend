INSERT INTO permissions (
    code,
    description,
    owning_service,
    sensitive,
    active
)
VALUES (
    'ACADEMIC_YEAR_STRUCTURE_ROLLOVER',
    'Copy reusable academic structure between academic years',
    'academic-service',
    TRUE,
    TRUE
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (
    role,
    permission_id
)
SELECT
    roles.role,
    permissions.id
FROM (
         VALUES
             ('ADMIN'),
             ('PRINCIPAL')
     ) AS roles(role)
         CROSS JOIN permissions
WHERE permissions.code = 'ACADEMIC_YEAR_STRUCTURE_ROLLOVER'
ON CONFLICT (role, permission_id) DO NOTHING;
