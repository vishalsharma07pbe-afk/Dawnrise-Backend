/*
 * Section permissions owned by academic-service.
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
        'SECTION_VIEW',
        'View academic sections',
        'academic-service',
        FALSE,
        TRUE
    ),
    (
        'SECTION_CREATE',
        'Create academic sections',
        'academic-service',
        TRUE,
        TRUE
    ),
    (
        'SECTION_UPDATE',
        'Update academic sections',
        'academic-service',
        TRUE,
        TRUE
    );


/*
 * Every school role can view the academic section structure.
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
WHERE permissions.code = 'SECTION_VIEW';


/*
 * Academic leadership can create and update sections.
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
                           'SECTION_CREATE',
                           'SECTION_UPDATE'
    );