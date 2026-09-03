INSERT INTO permissions (
    code,
    description,
    owning_service,
    sensitive,
    active
)
VALUES
    (
        'STUDENT_ENROLLMENT_VIEW',
        'View student enrollments',
        'academic-service',
        FALSE,
        TRUE
    ),
    (
        'STUDENT_ENROLLMENT_CREATE',
        'Create student enrollments',
        'academic-service',
        TRUE,
        TRUE
    ),
    (
        'STUDENT_ENROLLMENT_UPDATE',
        'Update student enrollment details',
        'academic-service',
        TRUE,
        TRUE
    ),
    (
        'STUDENT_ENROLLMENT_TRANSFER',
        'Transfer students between sections',
        'academic-service',
        TRUE,
        TRUE
    ),
    (
        'STUDENT_ENROLLMENT_WITHDRAW',
        'Withdraw students from an academic year',
        'academic-service',
        TRUE,
        TRUE
    ),
    (
        'STUDENT_ENROLLMENT_COMPLETE',
        'Complete student enrollments',
        'academic-service',
        TRUE,
        TRUE
    );


/*
 * Enrollment list/history views expose student membership across
 * sections. Until student ownership and parent relationships exist,
 * grant broad visibility only to staff roles with a school operations
 * need.
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
WHERE permissions.code = 'STUDENT_ENROLLMENT_VIEW';


/*
 * Academic leadership and admissions staff manage enrollment.
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
             ('ADMISSIONS_OFFICER')
     ) AS roles(role)
         CROSS JOIN permissions
WHERE permissions.code IN (
                           'STUDENT_ENROLLMENT_CREATE',
                           'STUDENT_ENROLLMENT_UPDATE',
                           'STUDENT_ENROLLMENT_TRANSFER',
                           'STUDENT_ENROLLMENT_WITHDRAW'
    );


/*
 * Completing an academic enrollment is restricted to academic
 * leadership and examination administration.
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
             ('EXAMINATION_CONTROLLER')
     ) AS roles(role)
         CROSS JOIN permissions
WHERE permissions.code =
      'STUDENT_ENROLLMENT_COMPLETE';
