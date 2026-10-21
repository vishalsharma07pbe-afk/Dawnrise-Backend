INSERT INTO permissions(code,description,owning_service,sensitive,active) VALUES
('TIMETABLE_SUBMIT','Submit a school-wide timetable draft for review','academic-service',TRUE,TRUE),
('TIMETABLE_REVIEW','Return a school-wide timetable with review findings','academic-service',TRUE,TRUE),
('TIMETABLE_APPROVE','Approve a reviewed school-wide timetable','academic-service',TRUE,TRUE),
('TIMETABLE_PUBLISH','Publish an effective-dated school-wide timetable','academic-service',TRUE,TRUE),
('TIMETABLE_PUBLISHED_VIEW','View published school timetables within staff scope','academic-service',FALSE,TRUE),
('TIMETABLE_PUBLISHED_VIEW_SELF','View a published timetable for oneself or an actively linked student','academic-service',FALSE,TRUE)
ON CONFLICT(code) DO NOTHING;

INSERT INTO role_permissions(role,permission_id)
SELECT role_name,p.id FROM (VALUES('ADMIN'),('PRINCIPAL'),('VICE_PRINCIPAL'),('EXAMINATION_CONTROLLER')) roles(role_name)
CROSS JOIN permissions p WHERE p.code IN ('TIMETABLE_SUBMIT','TIMETABLE_PUBLISHED_VIEW') ON CONFLICT DO NOTHING;
INSERT INTO role_permissions(role,permission_id)
SELECT role_name,p.id FROM (VALUES('PRINCIPAL'),('VICE_PRINCIPAL')) roles(role_name)
CROSS JOIN permissions p WHERE p.code IN ('TIMETABLE_REVIEW','TIMETABLE_APPROVE') ON CONFLICT DO NOTHING;
INSERT INTO role_permissions(role,permission_id)
SELECT role_name,p.id FROM (VALUES('ADMIN'),('PRINCIPAL')) roles(role_name)
CROSS JOIN permissions p WHERE p.code='TIMETABLE_PUBLISH' ON CONFLICT DO NOTHING;
INSERT INTO role_permissions(role,permission_id)
SELECT role_name,p.id FROM (VALUES('TEACHER'),('STUDENT'),('PARENT')) roles(role_name)
CROSS JOIN permissions p WHERE p.code='TIMETABLE_PUBLISHED_VIEW_SELF' ON CONFLICT DO NOTHING;
