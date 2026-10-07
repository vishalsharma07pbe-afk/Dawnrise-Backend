INSERT INTO permissions (code, description, owning_service, sensitive, active)
VALUES
    ('RESULT_VIEW', 'View subject result mark sheets', 'academic-service', FALSE, TRUE),
    ('RESULT_MARK_ENTRY', 'Enter and submit subject result marks', 'academic-service', TRUE, TRUE),
    ('RESULT_REVIEW', 'Review, return and approve subject result mark sheets', 'academic-service', TRUE, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT roles.role, p.id
FROM (VALUES
    ('ADMIN'), ('PRINCIPAL'), ('VICE_PRINCIPAL'),
    ('EXAMINATION_CONTROLLER'), ('TEACHER')
) AS roles(role)
CROSS JOIN permissions p
WHERE p.code = 'RESULT_VIEW'
ON CONFLICT (role, permission_id) DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT roles.role, p.id
FROM (VALUES
    ('TEACHER')
) AS roles(role)
CROSS JOIN permissions p
WHERE p.code IN ('RESULT_MARK_ENTRY', 'RESULT_REVIEW')
ON CONFLICT (role, permission_id) DO NOTHING;
