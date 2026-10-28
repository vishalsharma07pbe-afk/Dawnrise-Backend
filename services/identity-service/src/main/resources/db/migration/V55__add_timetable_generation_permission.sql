INSERT INTO permissions (code, description, owning_service, sensitive, active) VALUES
('TIMETABLE_GENERATE', 'Generate school-wide timetable draft proposals', 'academic-service', TRUE, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT role_name, p.id FROM (VALUES ('ADMIN'),('PRINCIPAL'),('EXAMINATION_CONTROLLER')) roles(role_name)
CROSS JOIN permissions p WHERE p.code='TIMETABLE_GENERATE' ON CONFLICT DO NOTHING;
