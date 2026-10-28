INSERT INTO permissions (code, description, owning_service, sensitive, active) VALUES
('TIMETABLE_CONFIG_VIEW', 'View timetable configuration and feasibility', 'academic-service', FALSE, TRUE),
('TIMETABLE_CONFIG_MANAGE', 'Manage timetable configuration', 'academic-service', TRUE, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT r.role, p.id
FROM (VALUES ('ADMIN'), ('PRINCIPAL'), ('VICE_PRINCIPAL'), ('EXAMINATION_CONTROLLER')) r(role)
CROSS JOIN permissions p
WHERE p.code = 'TIMETABLE_CONFIG_VIEW'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT r.role, p.id
FROM (VALUES ('ADMIN'), ('PRINCIPAL'), ('EXAMINATION_CONTROLLER')) r(role)
CROSS JOIN permissions p
WHERE p.code = 'TIMETABLE_CONFIG_MANAGE'
ON CONFLICT DO NOTHING;
