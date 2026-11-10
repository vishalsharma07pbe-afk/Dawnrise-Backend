INSERT INTO permissions(code, description, owning_service, sensitive, active) VALUES
('COMMUNICATION_NOTICE_SEND_SCOPED', 'Discover and send notices within assigned school scope', 'school-service', TRUE, TRUE),
('COMMUNICATION_NOTICE_SEND_SCHOOL_WIDE', 'Discover and send notices to the whole school', 'school-service', TRUE, TRUE)
ON CONFLICT(code) DO NOTHING;

INSERT INTO role_permissions(role, permission_id)
SELECT role_name, p.id
FROM (VALUES ('ADMIN'), ('PRINCIPAL'), ('VICE_PRINCIPAL'), ('TEACHER')) roles(role_name)
CROSS JOIN permissions p
WHERE p.code = 'COMMUNICATION_NOTICE_SEND_SCOPED'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role, permission_id)
SELECT role_name, p.id
FROM (VALUES ('ADMIN'), ('PRINCIPAL'), ('VICE_PRINCIPAL')) roles(role_name)
CROSS JOIN permissions p
WHERE p.code = 'COMMUNICATION_NOTICE_SEND_SCHOOL_WIDE'
ON CONFLICT DO NOTHING;
