INSERT INTO permissions (
    code,
    description,
    owning_service,
    sensitive,
    active
)
VALUES (
           'STUDENT_ANNUAL_PROGRESSION',
           'Preview and confirm student annual progression between academic years',
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
WHERE permissions.code = 'STUDENT_ANNUAL_PROGRESSION'
ON CONFLICT (role, permission_id) DO NOTHING;