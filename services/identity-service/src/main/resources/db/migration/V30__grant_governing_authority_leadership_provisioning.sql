/*
 * Allow the Governing Authority to initiate senior-leadership
 * provisioning.
 *
 * Application authorization still limits this role to creating:
 * - ADMIN
 * - PRINCIPAL
 * - GOVERNING_AUTHORITY
 *
 * UserService additionally permits direct assignment only for the
 * first ADMIN when no ADMIN exists in the organization. Subsequent
 * sensitive appointments use the approval workflow.
 */

INSERT INTO role_permissions (role, permission_id)
SELECT
    'GOVERNING_AUTHORITY',
    permissions.id
FROM permissions
WHERE permissions.code IN (
                           'USER_CREATE',
                           'ROLE_ASSIGNMENT_REQUEST_CREATE'
    )
  AND NOT EXISTS (
    SELECT 1
    FROM role_permissions existing
    WHERE existing.role = 'GOVERNING_AUTHORITY'
      AND existing.permission_id = permissions.id
);