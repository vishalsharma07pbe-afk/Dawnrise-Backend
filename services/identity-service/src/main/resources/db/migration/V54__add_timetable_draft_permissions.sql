INSERT INTO permissions (code, description, owning_service, sensitive, active) VALUES
('TIMETABLE_DRAFT_VIEW', 'View school timetable drafts', 'academic-service', FALSE, TRUE),
('TIMETABLE_DRAFT_EDIT', 'Create and manually edit school timetable drafts', 'academic-service', TRUE, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT role_name, p.id FROM (VALUES ('ADMIN'),('PRINCIPAL'),('VICE_PRINCIPAL'),('EXAMINATION_CONTROLLER')) roles(role_name)
CROSS JOIN permissions p WHERE p.code='TIMETABLE_DRAFT_VIEW' ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT role_name, p.id FROM (VALUES ('ADMIN'),('PRINCIPAL'),('EXAMINATION_CONTROLLER')) roles(role_name)
CROSS JOIN permissions p WHERE p.code='TIMETABLE_DRAFT_EDIT' ON CONFLICT DO NOTHING;
