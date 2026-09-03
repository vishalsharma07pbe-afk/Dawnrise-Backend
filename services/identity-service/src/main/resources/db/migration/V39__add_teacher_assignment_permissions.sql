INSERT INTO permissions (
    code,
    description,
    owning_service,
    sensitive,
    active
)
VALUES
    ('TEACHER_ASSIGNMENT_VIEW',
     'View teacher assignments',
     'academic-service', FALSE, TRUE),

    ('TEACHER_ASSIGNMENT_CREATE',
     'Create teacher assignments',
     'academic-service', TRUE, TRUE),

    ('TEACHER_ASSIGNMENT_UPDATE',
     'Update teacher assignments',
     'academic-service', TRUE, TRUE),

    ('TEACHER_ASSIGNMENT_REMOVE',
     'Remove teacher assignments',
     'academic-service', TRUE, TRUE);


INSERT INTO role_permissions (
    role,
    permission_id
)
SELECT roles.role, permissions.id
FROM (
         VALUES
             ('ADMIN'), ('PRINCIPAL'), ('VICE_PRINCIPAL'),
             ('TEACHER'), ('STUDENT'), ('PARENT'),
             ('ADMISSIONS_OFFICER'), ('EXAMINATION_CONTROLLER'),
             ('LIBRARIAN'), ('HR'), ('ACCOUNTANT'),
             ('TRANSPORT_MANAGER'), ('HOSTEL_STAFF'),
             ('INVENTORY_MANAGER'), ('SPORTS_STAFF'),
             ('SCIENCE_LAB_STAFF'), ('COMPUTER_LAB_STAFF'),
             ('MUSIC_STAFF'), ('MAINTENANCE_STAFF'),
             ('HOUSEKEEPING_STAFF'), ('GOVERNING_AUTHORITY')
     ) AS roles(role)
         CROSS JOIN permissions
WHERE permissions.code = 'TEACHER_ASSIGNMENT_VIEW';


INSERT INTO role_permissions (
    role,
    permission_id
)
SELECT roles.role, permissions.id
FROM (
         VALUES
             ('ADMIN'),
             ('PRINCIPAL'),
             ('VICE_PRINCIPAL')
     ) AS roles(role)
         CROSS JOIN permissions
WHERE permissions.code IN (
                           'TEACHER_ASSIGNMENT_CREATE',
                           'TEACHER_ASSIGNMENT_UPDATE',
                           'TEACHER_ASSIGNMENT_REMOVE'
    );