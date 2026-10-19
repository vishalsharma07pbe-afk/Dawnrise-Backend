INSERT INTO permissions (code, description, owning_service, sensitive, active) VALUES
('GRADING_POLICY_VIEW','View grading policies','academic-service',FALSE,TRUE),
('GRADING_POLICY_MANAGE','Create and activate grading policies','academic-service',TRUE,TRUE),
('REPORT_CARD_GENERATE','Generate report-card snapshots','academic-service',TRUE,TRUE),
('REPORT_CARD_REVIEW','Review report cards','academic-service',TRUE,TRUE),
('REPORT_CARD_PUBLISH','Publish approved report cards','academic-service',TRUE,TRUE),
('REPORT_CARD_VIEW_SELF','View own or linked-student report cards','academic-service',FALSE,TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role, permission_id)
SELECT r.role,p.id FROM (VALUES ('ADMIN'),('PRINCIPAL'),('VICE_PRINCIPAL'),('EXAMINATION_CONTROLLER'),('TEACHER')) r(role)
CROSS JOIN permissions p WHERE p.code='GRADING_POLICY_VIEW' ON CONFLICT DO NOTHING;
INSERT INTO role_permissions (role, permission_id)
SELECT r.role,p.id FROM (VALUES ('ADMIN'),('PRINCIPAL'),('EXAMINATION_CONTROLLER')) r(role)
CROSS JOIN permissions p WHERE p.code='GRADING_POLICY_MANAGE' ON CONFLICT DO NOTHING;
INSERT INTO role_permissions (role, permission_id)
SELECT r.role,p.id FROM (VALUES ('ADMIN'),('PRINCIPAL'),('VICE_PRINCIPAL'),('EXAMINATION_CONTROLLER')) r(role)
CROSS JOIN permissions p WHERE p.code='REPORT_CARD_GENERATE' ON CONFLICT DO NOTHING;
INSERT INTO role_permissions (role, permission_id)
SELECT r.role,p.id FROM (VALUES ('PRINCIPAL'),('VICE_PRINCIPAL'),('EXAMINATION_CONTROLLER')) r(role)
CROSS JOIN permissions p WHERE p.code='REPORT_CARD_REVIEW' ON CONFLICT DO NOTHING;
INSERT INTO role_permissions (role, permission_id)
SELECT r.role,p.id FROM (VALUES ('PRINCIPAL'),('EXAMINATION_CONTROLLER')) r(role)
CROSS JOIN permissions p WHERE p.code='REPORT_CARD_PUBLISH' ON CONFLICT DO NOTHING;
INSERT INTO role_permissions (role, permission_id)
SELECT r.role,p.id FROM (VALUES ('STUDENT'),('PARENT')) r(role)
CROSS JOIN permissions p WHERE p.code='REPORT_CARD_VIEW_SELF' ON CONFLICT DO NOTHING;
