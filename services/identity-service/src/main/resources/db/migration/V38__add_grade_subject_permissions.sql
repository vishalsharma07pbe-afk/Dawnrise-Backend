INSERT INTO permissions (
    code,
    description,
    owning_service,
    sensitive,
    active
)
VALUES
    ('GRADE_SUBJECT_VIEW',
     'View subjects assigned to grade levels',
     'academic-service', FALSE, TRUE),

    ('GRADE_SUBJECT_ASSIGN',
     'Assign subjects to grade levels',
     'academic-service', TRUE, TRUE),

    ('GRADE_SUBJECT_UPDATE',
     'Update grade-level subject assignments',
     'academic-service', TRUE, TRUE),

    ('GRADE_SUBJECT_REMOVE',
     'Remove subjects from grade levels',
     'academic-service', TRUE, TRUE);


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
WHERE permissions.code = 'GRADE_SUBJECT_VIEW';


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
                           'GRADE_SUBJECT_ASSIGN',
                           'GRADE_SUBJECT_UPDATE',
                           'GRADE_SUBJECT_REMOVE'
    );