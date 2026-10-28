INSERT INTO permissions(code,description,owning_service,sensitive,active) VALUES
('DAILY_COVERAGE_VIEW','View daily teacher absence and class coverage','academic-service',FALSE,TRUE),
('DAILY_COVERAGE_MANAGE','Record absences and prepare daily class coverage','academic-service',TRUE,TRUE),
('DAILY_COVERAGE_APPROVE','Approve or reject daily class coverage','academic-service',TRUE,TRUE),
('DAILY_COVERAGE_AUDIT_VIEW','View daily class coverage audit history','academic-service',FALSE,TRUE)
ON CONFLICT(code) DO NOTHING;

INSERT INTO role_permissions(role,permission_id)
SELECT role_name,p.id
FROM (VALUES('ADMIN'),('PRINCIPAL'),('VICE_PRINCIPAL')) roles(role_name)
CROSS JOIN permissions p
WHERE p.code IN ('DAILY_COVERAGE_VIEW','DAILY_COVERAGE_MANAGE','DAILY_COVERAGE_AUDIT_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role,permission_id)
SELECT role_name,p.id
FROM (VALUES('PRINCIPAL'),('VICE_PRINCIPAL')) roles(role_name)
CROSS JOIN permissions p
WHERE p.code='DAILY_COVERAGE_APPROVE'
ON CONFLICT DO NOTHING;
