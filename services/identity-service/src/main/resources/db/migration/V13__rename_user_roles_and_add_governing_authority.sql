-- Rename existing role values to the finalized EduSphere role names.
--
-- Roles are stored as VARCHAR values. Adding GOVERNING_AUTHORITY
-- does not require a schema alteration; it becomes available when
-- the Java enum is updated.

UPDATE user_roles
SET role = CASE role
               WHEN 'VICE_PRINCIPAL_HEADMASTER' THEN 'VICE_PRINCIPAL'
               WHEN 'ADMISSIONS'                THEN 'ADMISSIONS_OFFICER'
               WHEN 'SPORTS_DEPARTMENT'         THEN 'SPORTS_STAFF'
               WHEN 'SCIENCE_LAB'               THEN 'SCIENCE_LAB_STAFF'
               WHEN 'COMPUTER_LAB'              THEN 'COMPUTER_LAB_STAFF'
               WHEN 'MUSIC_DEPARTMENT'          THEN 'MUSIC_STAFF'
               WHEN 'MAINTENANCE'               THEN 'MAINTENANCE_STAFF'
               WHEN 'HOUSEKEEPING'              THEN 'HOUSEKEEPING_STAFF'
               WHEN 'HOSTEL'                    THEN 'HOSTEL_STAFF'
               ELSE role
    END
WHERE role IN (
               'VICE_PRINCIPAL_HEADMASTER',
               'ADMISSIONS',
               'SPORTS_DEPARTMENT',
               'SCIENCE_LAB',
               'COMPUTER_LAB',
               'MUSIC_DEPARTMENT',
               'MAINTENANCE',
               'HOUSEKEEPING',
               'HOSTEL'
    );

UPDATE role_assignment_requests
SET requested_role = CASE requested_role
                         WHEN 'VICE_PRINCIPAL_HEADMASTER' THEN 'VICE_PRINCIPAL'
                         WHEN 'ADMISSIONS'                THEN 'ADMISSIONS_OFFICER'
                         WHEN 'SPORTS_DEPARTMENT'         THEN 'SPORTS_STAFF'
                         WHEN 'SCIENCE_LAB'               THEN 'SCIENCE_LAB_STAFF'
                         WHEN 'COMPUTER_LAB'              THEN 'COMPUTER_LAB_STAFF'
                         WHEN 'MUSIC_DEPARTMENT'          THEN 'MUSIC_STAFF'
                         WHEN 'MAINTENANCE'               THEN 'MAINTENANCE_STAFF'
                         WHEN 'HOUSEKEEPING'              THEN 'HOUSEKEEPING_STAFF'
                         WHEN 'HOSTEL'                    THEN 'HOSTEL_STAFF'
                         ELSE requested_role
    END
WHERE requested_role IN (
                         'VICE_PRINCIPAL_HEADMASTER',
                         'ADMISSIONS',
                         'SPORTS_DEPARTMENT',
                         'SCIENCE_LAB',
                         'COMPUTER_LAB',
                         'MUSIC_DEPARTMENT',
                         'MAINTENANCE',
                         'HOUSEKEEPING',
                         'HOSTEL'
    );

UPDATE role_assignment_approvals
SET approver_role = CASE approver_role
                        WHEN 'VICE_PRINCIPAL_HEADMASTER' THEN 'VICE_PRINCIPAL'
                        WHEN 'ADMISSIONS'                THEN 'ADMISSIONS_OFFICER'
                        WHEN 'SPORTS_DEPARTMENT'         THEN 'SPORTS_STAFF'
                        WHEN 'SCIENCE_LAB'               THEN 'SCIENCE_LAB_STAFF'
                        WHEN 'COMPUTER_LAB'              THEN 'COMPUTER_LAB_STAFF'
                        WHEN 'MUSIC_DEPARTMENT'          THEN 'MUSIC_STAFF'
                        WHEN 'MAINTENANCE'               THEN 'MAINTENANCE_STAFF'
                        WHEN 'HOUSEKEEPING'              THEN 'HOUSEKEEPING_STAFF'
                        WHEN 'HOSTEL'                    THEN 'HOSTEL_STAFF'
                        ELSE approver_role
    END
WHERE approver_role IN (
                        'VICE_PRINCIPAL_HEADMASTER',
                        'ADMISSIONS',
                        'SPORTS_DEPARTMENT',
                        'SCIENCE_LAB',
                        'COMPUTER_LAB',
                        'MUSIC_DEPARTMENT',
                        'MAINTENANCE',
                        'HOUSEKEEPING',
                        'HOSTEL'
    );
