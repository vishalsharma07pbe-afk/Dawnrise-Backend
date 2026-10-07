INSERT INTO permissions (code, description, owning_service, sensitive, active)
VALUES
    ('EXAMINATION_SCHEDULE_VIEW', 'View examination schedules', 'academic-service', FALSE, TRUE),
    ('EXAMINATION_SCHEDULE_MANAGE', 'Create, edit and publish examination schedules', 'academic-service', TRUE, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT roles.role, p.id
FROM (VALUES
    ('ADMIN'), ('PRINCIPAL'), ('VICE_PRINCIPAL'),
    ('EXAMINATION_CONTROLLER'), ('TEACHER'), ('STUDENT'), ('PARENT')
) AS roles(role)
CROSS JOIN permissions p
WHERE p.code = 'EXAMINATION_SCHEDULE_VIEW'
ON CONFLICT (role, permission_id) DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT roles.role, p.id
FROM (VALUES
    ('ADMIN'), ('PRINCIPAL'), ('VICE_PRINCIPAL'), ('EXAMINATION_CONTROLLER')
) AS roles(role)
CROSS JOIN permissions p
WHERE p.code = 'EXAMINATION_SCHEDULE_MANAGE'
ON CONFLICT (role, permission_id) DO NOTHING;
