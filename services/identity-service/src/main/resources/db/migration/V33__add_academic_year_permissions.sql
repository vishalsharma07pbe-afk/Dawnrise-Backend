/*
 * Academic-year permissions owned by academic-service.
 */
INSERT INTO permissions (
    code,
    description,
    owning_service,
    sensitive,
    active
)
VALUES
    (
        'ACADEMIC_YEAR_VIEW',
        'View academic years',
        'academic-service',
        FALSE,
        TRUE
    ),
    (
        'ACADEMIC_YEAR_CREATE',
        'Create academic years',
        'academic-service',
        TRUE,
        TRUE
    ),
    (
        'ACADEMIC_YEAR_UPDATE',
        'Update planned academic years',
        'academic-service',
        TRUE,
        TRUE
    ),
    (
        'ACADEMIC_YEAR_ACTIVATE',
        'Activate an academic year',
        'academic-service',
        TRUE,
        TRUE
    ),
    (
        'ACADEMIC_YEAR_CLOSE',
        'Close the active academic year',
        'academic-service',
        TRUE,
        TRUE
    );

/*
 * Every school role can view academic years.
 *
 * Other modules will need the active academic year for classes,
 * enrollment, attendance, timetables, examinations, and dashboards.
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
WHERE permissions.code = 'ACADEMIC_YEAR_VIEW';


/*
 * School academic leadership can prepare academic years.
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
                           'ACADEMIC_YEAR_CREATE',
                           'ACADEMIC_YEAR_UPDATE'
    );


/*
 * Activating or closing an academic year affects the whole school,
 * so these operations are restricted to admin and principal.
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
             ('PRINCIPAL')
     ) AS roles(role)
         CROSS JOIN permissions
WHERE permissions.code IN (
                           'ACADEMIC_YEAR_ACTIVATE',
                           'ACADEMIC_YEAR_CLOSE'
    );