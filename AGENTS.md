# Dawnrise Backend — Codex Instructions

## Purpose

Dawnrise is a multi-tenant school ERP backend.

This repository is a Spring Boot monorepo containing:

- `services/identity-service`
- `services/school-service`
- `services/academic-service`

Read this file before making changes.

## Technology

- Java 21
- Spring Boot
- Spring Security OAuth2 Resource Server
- Spring Data JPA
- PostgreSQL
- Flyway
- Maven Wrapper
- JUnit and Mockito

Use the Maven wrapper supplied by each service.

## Service ownership

### identity-service

Owns:

- Authentication
- JWT generation
- Users
- Roles
- Permissions
- Account lifecycle
- Refresh-token sessions
- Tenant-safe identity validation APIs

Default local port: `8081`.

### school-service

Owns:

- School and organization provisioning
- School information
- Branding
- School timezone

Default local port: `8080`.

### academic-service

Owns:

- Academic years
- Grade levels
- Sections
- Subjects
- Grade-subject mappings
- Teacher assignments
- Student enrollments
- Academic calendar
- Student progression
- Student attendance

Default local port: `8082`.

Do not move domain ownership between services without explicit approval.

## Tenant isolation

- Tenant identity comes from the authenticated JWT `organizationId` claim.
- Actor identity comes from the JWT subject.
- Never trust tenant IDs or actor IDs supplied in request bodies or query parameters.
- Repository and service operations must remain organization-scoped.
- Cross-tenant access must fail safely.
- Do not weaken `@PreAuthorize` rules.
- Do not replace permission checks with role-name assumptions.
- Reuse existing tenant-security and authenticated-actor helpers.

## Inter-service rules

- Academic records store identity user IDs as external references.
- Do not create JPA relationships across service databases.
- Use existing internal service clients for cross-service validation.
- Preserve internal API authentication headers and safe failure handling.
- Do not silently continue when required tenant or identity validation fails.

## Database migrations

- Flyway migrations are immutable after being applied or merged.
- Never edit an existing migration unless the task explicitly confirms it is unapplied everywhere.
- Add the next migration version after inspecting the current migration directory.
- Use tenant-safe foreign keys and constraints.
- Add database constraints for important invariants.
- Test migrations without modifying earlier migration history.
- Never apply migrations unless explicitly requested.

## API contracts

Before changing an API:

1. Inspect the controller.
2. Inspect request and response DTOs.
3. Inspect validation annotations.
4. Inspect security permissions.
5. Inspect service transaction boundaries.
6. Inspect exception mappings.
7. Update affected tests and frontend-contract documentation.

Do not guess endpoint paths, enum values, request fields, version fields, or permissions.

## Concurrency

- Preserve JPA `@Version` behavior.
- Mutation endpoints should use expected versions where established.
- Compare expected versions inside the transaction after acquiring the required lock.
- Return safe HTTP `409 Conflict` responses for stale mutations.
- Do not implement automatic retries for user-driven stale writes.
- Validate all records before mutating any item in a bulk operation.

## Java conventions

- Follow the existing package structure.
- Prefer records for immutable DTOs.
- Keep controllers thin.
- Put business rules in services or focused policy/validator components.
- Use constructor injection.
- Avoid wildcard imports in new code.
- Normalize optional text consistently.
- Use deterministic ordering for collections returned by APIs.
- Do not expose JPA entities directly from controllers.
- Preserve safe exception messages.

## Scope discipline

For each task:

- Read only the relevant module and directly related shared code.
- Make the smallest coherent change.
- Preserve unrelated user changes.
- Do not refactor unrelated packages.
- Do not add speculative abstractions.
- Do not implement future phases unless requested.
- Stop and report if the requested change requires broader authority or a breaking decision.

## Testing policy

During implementation, prefer:

1. Compile
2. Focused unit/controller/repository tests
3. `git diff --check`

Run the full service test suite once when the module is complete or when shared infrastructure changes.

Typical commands:

```bash
cd services/academic-service
./mvnw -DskipTests compile
./mvnw -Dtest='RelevantTest1,RelevantTest2' test
./mvnw test