/*
 * Voiding preserves an incorrectly created academic year for audit
 * while allowing a corrected replacement to be created.
 */
INSERT INTO permissions (
    code,
    description,
    owning_service,
    sensitive,
    active
)
VALUES (
           'ACADEMIC_YEAR_VOID',
           'Void an incorrectly created academic year',
           'academic-service',
           TRUE,
           TRUE
       );


/*
 * Voiding is a sensitive correction operation.
 * Restrict it to the principal and administrator.
 */
INSERT INTO role_permissions (
    role,
    permission_id
)
SELECT
    roles.role,
    permissions.id
FROM (
         VALUES
             ('ADMIN'),
             ('PRINCIPAL')
     ) AS roles(role)
         CROSS JOIN permissions
WHERE permissions.code = 'ACADEMIC_YEAR_VOID';