/*
 * ADMIN is a required approver for several sensitive role assignments.
 *
 * Requesters still cannot approve their own requests. This permission
 * allows an ADMIN to review requests initiated by another authorized
 * user, such as a GOVERNING_AUTHORITY or HR user.
 */

INSERT INTO role_permissions (role, permission_id)
SELECT
    'ADMIN',
    permissions.id
FROM permissions
WHERE permissions.code = 'ROLE_ASSIGNMENT_APPROVE'
  AND NOT EXISTS (
    SELECT 1
    FROM role_permissions existing
    WHERE existing.role = 'ADMIN'
      AND existing.permission_id = permissions.id
);