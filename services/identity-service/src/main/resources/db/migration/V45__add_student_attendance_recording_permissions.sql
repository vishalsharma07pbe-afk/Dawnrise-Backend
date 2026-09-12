INSERT INTO permissions (
    code,
    description,
    owning_service,
    sensitive,
    active
)
VALUES
    (
        'STUDENT_ATTENDANCE_VIEW',
        'View recorded student attendance',
        'academic-service',
        FALSE,
        TRUE
    ),
    (
        'STUDENT_ATTENDANCE_RECORD',
        'Create and update draft student attendance records',
        'academic-service',
        TRUE,
        TRUE
    ),
    (
        'STUDENT_ATTENDANCE_SUBMIT',
        'Submit student attendance sessions',
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
WHERE permissions.code IN (
    'STUDENT_ATTENDANCE_VIEW',
    'STUDENT_ATTENDANCE_RECORD',
    'STUDENT_ATTENDANCE_SUBMIT'
)
ON CONFLICT (role, permission_id) DO NOTHING;
