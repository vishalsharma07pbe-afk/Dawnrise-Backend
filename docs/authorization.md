# Dawnrise Authorization Contract

Identity service is the authorization authority for identity-scoped actions. It issues access tokens with:

- `roles`: RBAC role names, converted by Spring Security to `ROLE_<ROLE_NAME>`.
- `permissions`: active effective `PermissionCode` names, converted with no prefix.
- `organizationId`: tenant boundary for all organization-scoped APIs.

Resource services must enforce their own resource scopes. A permission answers what action is allowed; the service owning the data answers where, on whom, and under which relationship.

## Permission Naming

Use `<RESOURCE>_<ACTION>` when a service and its use cases are implemented.

Examples:

- `ATTENDANCE_VIEW`
- `ATTENDANCE_MARK`
- `ATTENDANCE_CORRECT`
- `FEE_VIEW`
- `FEE_COLLECT`
- `FEE_REFUND`

Do not add speculative permissions before the service exposes real behavior.

## Required Scope Checks

Future services must combine token permissions with resource-scope checks such as:

- School and branch administration: branch administrators are restricted to assigned branches.
- Students and parents: parents may access only linked students; students may access only self-owned records.
- Teacher assignments: teachers are restricted to assigned classes, sections, subjects, and students.
- Attendance: marking and correction require assignment to the class or delegated authority.
- Timetable: edits require ownership of the timetable scope or delegated branch/school authority.
- Examinations and grades: teachers can manage assigned subjects; examination staff can manage configured exam scopes.
- Fees and accounting: accountants are restricted to financial operations and configured branches.
- Library: librarians can operate library workflows for assigned library scopes.
- Transport: transport staff are restricted to routes, vehicles, and riders they manage.
- Hostel: hostel staff are restricted to assigned hostel blocks or residents.
- Inventory: inventory staff are restricted to assigned stores, departments, or branches.
- Sports: sports staff are restricted to sports department operations and assigned students or teams.
- Laboratories: lab staff are restricted to assigned labs and practical-work scopes.
- Music: music staff are restricted to music department operations and assigned students or groups.
- Maintenance: maintenance staff are restricted to work orders and assigned facilities.
- Housekeeping: housekeeping staff are restricted to assigned facilities and work orders.
- HR: HR staff are restricted to employee provisioning and employment operations.
- Notifications and communication: senders are restricted by audience, tenant, branch, class, and ownership rules.

## Token Freshness

Changing role-permission mappings affects newly issued access tokens. Login and refresh both reload current active permissions. Existing access tokens may retain old permissions until their short expiry. Suspension, deactivation, and password change revoke refresh-token families; this does not blacklist already issued access tokens.

If immediate access-token invalidation becomes required later, add a token security-version or blacklist mechanism deliberately rather than overloading permission checks.
