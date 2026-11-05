INSERT INTO permissions(code, description, owning_service, sensitive, active)
VALUES ('STUDENT_ATTENDANCE_GUARDIAN_VIEW', 'View submitted attendance for actively linked students', 'academic-service', FALSE, TRUE)
ON CONFLICT(code) DO NOTHING;

INSERT INTO role_permissions(role, permission_id)
SELECT 'PARENT', p.id FROM permissions p
WHERE p.code = 'STUDENT_ATTENDANCE_GUARDIAN_VIEW'
ON CONFLICT DO NOTHING;
