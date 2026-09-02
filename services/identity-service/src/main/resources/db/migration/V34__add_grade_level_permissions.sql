INSERT INTO permissions (
    code,
    description,
    owning_service,
    sensitive,
    active
)
VALUES
    (
        'GRADE_LEVEL_VIEW',
        'View grade levels',
        'academic-service',
        FALSE,
        TRUE
    ),
    (
        'GRADE_LEVEL_CREATE',
        'Create grade levels',
        'academic-service',
        TRUE,
        TRUE
    ),
    (
        'GRADE_LEVEL_UPDATE',
        'Update grade levels',
        'academic-service',
        TRUE,
        TRUE
    );

/*
 * Every school role can view grade levels.
 *
 * Academic modules need grade-level context for enrollment,
 * attendance, timetables, examinations, and dashboards.
 */
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
             ('PARENT'),
             ('ADMISSIONS_OFFICER'),
             ('EXAMINATION_CONTROLLER'),
             ('LIBRARIAN'),
             ('HR'),
             ('ACCOUNTANT'),
             ('TRANSPORT_MANAGER'),
             ('HOSTEL_STAFF'),
             ('INVENTORY_MANAGER'),
             ('SPORTS_STAFF'),
             ('SCIENCE_LAB_STAFF'),
             ('COMPUTER_LAB_STAFF'),
             ('MUSIC_STAFF'),
             ('MAINTENANCE_STAFF'),
             ('HOUSEKEEPING_STAFF'),
             ('GOVERNING_AUTHORITY')
     ) AS roles(role)
         CROSS JOIN permissions
WHERE permissions.code = 'GRADE_LEVEL_VIEW';


/*
 * School academic leadership can prepare Grade level.
 */
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
WHERE permissions.code IN (
                           'GRADE_LEVEL_CREATE',
                           'GRADE_LEVEL_UPDATE'
    );