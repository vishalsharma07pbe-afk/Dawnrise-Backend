INSERT INTO permissions (code, description, owning_service, sensitive, active)
VALUES
    ('RESULT_PUBLISH', 'Review readiness and publish complete section results', 'academic-service', TRUE, TRUE),
    ('RESULT_PUBLISHED_VIEW_SELF', 'View published results for self or a linked student', 'academic-service', FALSE, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT roles.role, permissions.id
FROM (VALUES
    ('ADMIN'), ('PRINCIPAL'), ('VICE_PRINCIPAL'), ('EXAMINATION_CONTROLLER')
) AS roles(role)
CROSS JOIN permissions
WHERE permissions.code = 'RESULT_PUBLISH'
ON CONFLICT (role, permission_id) DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT roles.role, permissions.id
FROM (VALUES ('STUDENT'), ('PARENT')) AS roles(role)
CROSS JOIN permissions
WHERE permissions.code = 'RESULT_PUBLISHED_VIEW_SELF'
ON CONFLICT (role, permission_id) DO NOTHING;
