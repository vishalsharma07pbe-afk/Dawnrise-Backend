# Dawnrise Backend

Dawnrise Backend is the service layer for a multi-tenant school-management platform. It is being built as a set of Spring Boot services with a security-first identity foundation: organization-scoped users, role and permission governance, token-based authentication, approval workflows for sensitive access, and an append-only security audit trail.

## What This Repository Contains

```text
Dawnrise-Backend/
  services/
    identity-service/   Authentication, users, permissions, role governance, audit trail
    school-service/     School-domain service scaffold
  docs/
    authorization.md    Authorization contract for future services
```

The most complete service today is `identity-service`. It owns identity-scoped security decisions and issues JWT access tokens containing:

- `organizationId` for tenant isolation;
- `roles` for coarse application identity;
- `permissions` for exact API authorization.

## Identity Service Highlights

The identity service currently supports:

- username/password login with lockout escalation;
- short-lived JWT access tokens;
- refresh-token rotation and session revocation;
- account activation and password reset token flows;
- organization-scoped user administration;
- role-to-permission mapping seeded by Flyway;
- routine role assignment/removal with explicit permissions;
- sensitive role-assignment approval workflow;
- sensitive role-removal approval workflow;
- protected governance-role safeguards;
- append-only security audit events.

## Security Model

Dawnrise separates three security questions:

- **Who are you?** JWT subject and user record.
- **What can you do?** `PermissionCode` authorities.
- **Where can you do it?** organization and resource-scope checks.

Every organization-scoped controller preserves tenant authorization through `TenantSecurity`. Permissions are exact action names such as `USER_CREATE`, `ROLE_REMOVE_ROUTINE`, `ROLE_REMOVAL_APPROVE`, and `SECURITY_AUDIT_VIEW`.

See [docs/authorization.md](docs/authorization.md) for the broader authorization contract.

## Sensitive Role Governance

Routine roles can be changed directly when the caller has the correct routine role permission.

Sensitive roles are different. They cannot be casually added or removed through ordinary role replacement:

- sensitive additions create role-assignment approval requests;
- sensitive removals create role-removal approval requests;
- approval eligibility is decided by policy, not permission alone;
- final sensitive role removal revokes the target user’s refresh-token sessions;
- short-lived access tokens may retain old claims until expiry.

The workflow protects critical accounts such as the last active `ADMIN` and `GOVERNING_AUTHORITY` in an organization.

## Security Audit Trail

The audit trail is append-only from application services. It records security-sensitive outcomes without storing secrets.

Audited actions include:

- login success and failure;
- password changes;
- user creation;
- user status changes;
- routine role assignment and removal;
- sensitive assignment request, cancellation, decision, and final assignment;
- sensitive removal request, cancellation, decision, and final removal.

Audit read access is tenant-isolated and protected by `SECURITY_AUDIT_VIEW`.

## Key Identity Endpoints

```text
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/password/change

GET  /api/v1/organizations/{organizationId}/users
POST /api/v1/organizations/{organizationId}/users
PUT  /api/v1/organizations/{organizationId}/users/{userId}/roles
PUT  /api/v1/organizations/{organizationId}/users/{userId}/status

POST /api/v1/organizations/{organizationId}/role-requests
GET  /api/v1/organizations/{organizationId}/role-requests/actionable
POST /api/v1/organizations/{organizationId}/role-requests/{requestId}/decisions
POST /api/v1/organizations/{organizationId}/role-requests/{requestId}/cancel

POST /api/v1/organizations/{organizationId}/role-removal-requests
GET  /api/v1/organizations/{organizationId}/role-removal-requests/actionable
POST /api/v1/organizations/{organizationId}/role-removal-requests/{requestId}/decisions
POST /api/v1/organizations/{organizationId}/role-removal-requests/{requestId}/cancel

GET  /api/v1/organizations/{organizationId}/security-audit-events
```

## Local Development

Requirements:

- Java 21;
- PostgreSQL for service startup and Flyway validation;
- Maven wrapper included per service.

Run identity-service tests:

```bash
cd services/identity-service
./mvnw clean test
```

Start identity-service locally:

```bash
cd services/identity-service
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

The default service port is `8081`.

## Database Migrations

Identity-service uses Flyway. Applied migrations must not be edited after release. New schema work should use the next versioned migration.

Current identity-service migration line includes:

- `V18__create_role_removal_approval_tables.sql`
- `V19__create_security_audit_events.sql`

## Verification Status

Latest local verification:

```text
./mvnw clean test
Tests run: 212, Failures: 0, Errors: 0, Skipped: 0
```

Startup verification also confirmed Flyway validation, V19 migration application, Hibernate schema validation, and clean service shutdown.

## Project Direction

Dawnrise is moving toward a modular school platform where the identity service acts as the authorization authority and domain services enforce their own resource scopes. The current backend emphasizes correctness in tenant isolation, permission checks, approval workflows, token lifecycle, and auditability before broad feature expansion.
