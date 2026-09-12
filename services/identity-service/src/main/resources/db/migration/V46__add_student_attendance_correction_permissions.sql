INSERT INTO permissions (
    code,
    description,
    owning_service,
    sensitive,
    active
)
VALUES
    (
        'STUDENT_ATTENDANCE_CORRECTION_REQUEST',
        'Request corrections to submitted student attendance',
        'academic-service',
        TRUE,
        TRUE
    ),
    (
        'STUDENT_ATTENDANCE_CORRECTION_VIEW',
        'View student attendance correction requests',
        'academic-service',
        FALSE,
        TRUE
    ),
    (
        'STUDENT_ATTENDANCE_CORRECTION_APPROVE',
        'Approve or reject student attendance correction requests',
        'academic-service',
        TRUE,
        TRUE
    )
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (
    role,
    permission_id
)
SELECT roles.role, permissions.id
FROM (
         VALUES
             ('TEACHER'),
             ('ADMIN'),
             ('PRINCIPAL'),
             ('VICE_PRINCIPAL')
     ) AS roles(role)
         CROSS JOIN permissions
WHERE permissions.code IN (
    'STUDENT_ATTENDANCE_CORRECTION_REQUEST',
    'STUDENT_ATTENDANCE_CORRECTION_VIEW'
)
ON CONFLICT (role, permission_id) DO NOTHING;

INSERT INTO role_permissions (
    role,
    permission_id
)
SELECT roles.role, permissions.id
FROM (
         VALUES
             ('ADMIN'),
             ('PRINCIPAL')
     ) AS roles(role)
         CROSS JOIN permissions
WHERE permissions.code = 'STUDENT_ATTENDANCE_CORRECTION_APPROVE'
ON CONFLICT (role, permission_id) DO NOTHING;
