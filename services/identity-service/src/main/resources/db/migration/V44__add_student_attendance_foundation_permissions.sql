INSERT INTO permissions (
    code,
    description,
    owning_service,
    sensitive,
    active
)
VALUES
    (
        'STUDENT_ATTENDANCE_POLICY_VIEW',
        'View student attendance policy',
        'academic-service',
        FALSE,
        TRUE
    ),
    (
        'STUDENT_ATTENDANCE_POLICY_MANAGE',
        'Initialize and update student attendance policy',
        'academic-service',
        TRUE,
        TRUE
    ),
    (
        'STUDENT_ATTENDANCE_CALENDAR_VIEW',
        'View student attendance calendar days',
        'academic-service',
        FALSE,
        TRUE
    ),
    (
        'STUDENT_ATTENDANCE_CALENDAR_MANAGE',
        'Initialize and update student attendance calendar days',
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
             ('PRINCIPAL'),
             ('VICE_PRINCIPAL'),
             ('TEACHER')
     ) AS roles(role)
         CROSS JOIN permissions
WHERE permissions.code = 'STUDENT_ATTENDANCE_POLICY_VIEW'
ON CONFLICT (role, permission_id) DO NOTHING;

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
             ('PRINCIPAL'),
             ('VICE_PRINCIPAL')
     ) AS roles(role)
         CROSS JOIN permissions
WHERE permissions.code = 'STUDENT_ATTENDANCE_POLICY_MANAGE'
ON CONFLICT (role, permission_id) DO NOTHING;

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
             ('PRINCIPAL'),
             ('VICE_PRINCIPAL'),
             ('TEACHER'),
             ('STUDENT'),
             ('PARENT')
     ) AS roles(role)
         CROSS JOIN permissions
WHERE permissions.code = 'STUDENT_ATTENDANCE_CALENDAR_VIEW'
ON CONFLICT (role, permission_id) DO NOTHING;

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
             ('PRINCIPAL'),
             ('VICE_PRINCIPAL')
     ) AS roles(role)
         CROSS JOIN permissions
WHERE permissions.code = 'STUDENT_ATTENDANCE_CALENDAR_MANAGE'
ON CONFLICT (role, permission_id) DO NOTHING;
