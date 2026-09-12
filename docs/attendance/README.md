# Dawnrise Attendance Module — Architecture and Implementation Specification

## 1. Purpose

This document is the source of truth for completing Dawnrise attendance. Codex should inspect the repository before changing code, preserve existing conventions, and implement the remaining work phase by phase without requiring a new requirements prompt for every phase.

Canonical backend repository:

`/Users/vishalsharma/Developer/Dawnrise-Backend`

Obsolete clone that must never be touched:

`/Users/vishalsharma/Desktop/Dawnrise/Dawnrise-Backend`

Current development branch:

`feat/student-attendance-recording`

Technology baseline:

- Java 21 and Spring Boot 4.1.1
- PostgreSQL and Flyway
- JPA with `ddl-auto: validate`
- OAuth2 JWT resource servers
- `organizationId` tenant claim and JWT `sub` authenticated-user ID
- Optimistic locking for editable state
- Explicit transactional boundaries and database constraints

## 2. Domain ownership

Attendance is one product area with two bounded contexts.

### Student attendance

Owned by `academic-service` because it depends on academic years, calendars, grades, sections, student enrollments and teacher assignments.

### Teacher and staff attendance

Owned by a future `workforce-service` because it depends on employees, shifts, devices, punch events, leave and payroll-facing calculations. It must not be added to student-attendance tables or `school-service`.

### Shared services

- `identity-service`: users, roles, permissions and internal eligibility checks
- `school-service`: organization profile and authoritative IANA timezone
- Future notification service: email/SMS/WhatsApp delivery

The services must not use distributed transactions. Durable outbox events should represent cross-service outcomes.

## 3. Current implemented state

The repository is authoritative; Codex must verify these claims before continuing.

### Applied and immutable migrations

- Academic V11: attendance policy, status policy and academic calendar
- Academic V12: late-penalty policy
- Academic V13: student attendance sessions and records
- Academic V14: deferred-entry and automatic-submission policy
- Identity V44: attendance foundation permissions
- Identity V45: attendance recording permissions

Applied migrations must never be edited. Corrections require a new migration.

### Implemented student capabilities

- Daily attendance mode
- Statuses: `PRESENT`, `ABSENT`, `LATE`, `HALF_DAY`, `EXCUSED`
- Earned/possible credit policies
- Monthly late threshold with effective `HALF_DAY` or `ABSENT` outcome
- Working days, weekly offs, holidays, vacations, exam days, special functions and special working days
- One attendance session per tenant/year/section/date
- Draft creation/retrieval
- Bulk draft recording, maximum 100 students
- Manual submission
- Original and effective status stored separately
- Credit snapshots stored on attendance records
- Tenant-safe session, section, calendar and enrollment relationships
- Teacher access restricted to assigned sections
- Leadership tenant-wide access
- Submitted records are read-only through normal recording endpoints
- Active-year mutation checks
- Historical reads after year closure
- Authoritative school-timezone lookup
- Future-date rejection using school-local date
- Deferred-entry settings and role-based back-entry windows
- Automatic-submission foundation, disabled by default
- Bounded automatic-submission candidate processing
- Per-session locking, inactivity cutoff, exact-roster completeness and failure isolation

### Current in-progress checkpoint

Student correction workflow has been implemented in the working tree using:

- Academic V15 correction migration
- Identity V46 correction permissions
- Correction entities, DTOs, repositories, service, controller and tests

V15 and V46 must be reviewed, dry-run and applied before becoming immutable. Codex must inspect Git/Flyway state rather than assuming this checkpoint remains unchanged.

## 4. Student attendance functional rules

### 4.1 Calendar eligibility

Attendance can be recorded only when an academic calendar day exists and is eligible/counting.

- `WORKING_DAY` and `SPECIAL_WORKING_DAY`: normally required
- `WEEKLY_OFF`, `HOLIDAY`, `VACATION`: not applicable
- `EXAM_DAY` and `SPECIAL_FUNCTION`: follow their configured requirement

Public, national, festival, local, mandatory and weather closures can initially use `HOLIDAY` plus `name` and `note`. A separate general event calendar will eventually support multiple celebrations/events per date and is outside attendance.

### 4.2 Date and academic-year rules

- Mutations require an `ACTIVE` academic year.
- Reads remain available for historical `CLOSED` years.
- Future attendance dates are forbidden using the authoritative school-local date.
- Deferred entry must be enabled to enter a past date.
- Teachers are limited by `teacherBackEntryDays`.
- `ADMIN`, `PRINCIPAL` and `VICE_PRINCIPAL` use `leadershipBackEntryDays`.
- Leadership window cannot be smaller than the teacher window.

### 4.3 Enrollment rules

- The student enrollment must match organization, academic year, grade and section.
- Enrollment must cover the attendance date.
- Historical transferred/withdrawn/completed enrollment is valid only for dates within its effective period.
- One record per enrollment and one record per student in a session.
- Submission requires exact coverage of the eligible roster, not merely a subset.

### 4.4 Late penalties

- Teacher-selected status is `recordedStatus`.
- Policy-adjusted result is `effectiveStatus`.
- Without a penalty, both values must match.
- With a penalty, recorded status is `LATE` and effective status is `HALF_DAY` or `ABSENT`.
- Earned/possible credits are immutable snapshots so future policy changes do not rewrite history.

### 4.5 Session lifecycle

- `DRAFT`: records can be saved/replaced subject to version checks.
- `SUBMITTED`: normal editing is prohibited.
- Manual submission records the authenticated submitter.
- Automatic submission has no fake human submitter.
- Automatic submission is opt-in, operates after configured inactivity, and never submits an empty or incomplete roster.

## 5. Remaining student attendance work

Codex should continue these phases in order. Each phase must compile and pass focused tests before the next begins.

### Phase S1 — Finish correction workflow checkpoint

Review, test and finalize V15/V46 and the existing correction implementation.

Required lifecycle:

- Create a correction request only for a submitted session.
- Request contains one to 100 unique attendance record changes and a reason.
- Tenant, student, original values and audit users come from persisted/JWT data, never the request body.
- Store immutable previous and proposed snapshots.
- Creating a request does not alter submitted attendance.
- One pending request per session unless the implemented design safely allows independent non-overlapping requests.
- Requester may cancel a pending request.
- Authorized reviewer may approve or reject.
- Requester cannot review their own request.
- Approval reloads/locks records and rejects stale versions atomically.
- Approval applies exactly the stored proposed snapshots and keeps the session submitted.
- Original session submission metadata remains unchanged.
- All foreign keys use `ON DELETE RESTRICT` for durable history.

Permissions:

- `STUDENT_ATTENDANCE_CORRECTION_REQUEST`
- `STUDENT_ATTENDANCE_CORRECTION_VIEW`
- `STUDENT_ATTENDANCE_CORRECTION_APPROVE`

Teacher access remains assigned-section scoped. Approval is restricted to `ADMIN` and `PRINCIPAL`. No student/parent grant.

### Phase S2 — Idempotent offline/deferred synchronization

Provide backend synchronization for a later offline frontend:

- Client-generated idempotency key
- Canonical request hash
- Base session version
- Academic year/grade/section/date context
- One to 100 records
- Partial or complete draft sync
- Same key and same payload returns the original outcome
- Same key with a different payload returns `409`
- Concurrent changes/submission are reported, never overwritten
- Submitted attendance cannot be changed by synchronization
- Tenant/hierarchy/calendar/enrollment/date/role rules remain enforced
- Preserve request order
- Persist durable idempotency/sync operations through a new migration

The backend does not implement IndexedDB. The frontend later stores encrypted local drafts and calls this API when connectivity returns.

### Phase S3 — CSV/XLSX import

Implement template, preview and confirm:

- Bounded file size and row count
- CSV and XLSX support
- No spreadsheet formula evaluation
- Formula-injection-safe exports
- Prefer school-facing year/grade/section codes and roll/admission identifiers over raw database IDs
- Preview performs no attendance writes
- Return row number, normalized values and row-level errors
- Detect duplicates, unknown students, wrong tenant/section, invalid status/date and submitted-session conflicts
- Confirm requires preview fingerprint and idempotency key
- Confirm is transactional/all-or-nothing
- Confirm creates or updates a draft and never silently submits

### Phase S4 — Reporting and scoped views

Implement paginated, tenant-safe APIs for:

- Section daily register/summary
- Student date-range history
- Student monthly summary
- Attendance percentage based on stored credit snapshots
- Low-attendance students
- Late-occurrence summary
- Safe CSV export

Authorization:

- Leadership: tenant-wide
- Teacher: assigned sections only
- Student: self only
- Parent: linked children only

Student/parent grants must not be added until existing guardian relationship scope is inspected and proven fail-closed.

### Phase S5 — Notification outbox

After successful submission or approved correction, transactionally emit idempotent outbox events for:

- Absence
- Late arrival
- Effective half-day/absence caused by late policy

Do not send email, SMS or WhatsApp directly. Delivery, retries and provider integration belong to a notification service.

### Phase S6 — Student attendance frontend

Frontend capabilities:

- Year/grade/section/date selection
- Roster with roll numbers
- Mark all present
- Individual status and remarks
- Save draft and submit confirmation
- Read-only submitted view
- Holiday/non-working-day explanations
- Deferred entry
- Offline encrypted local draft and synchronization
- Import preview/confirm
- Correction request/review screens
- Reports
- Student self and parent-child views

## 6. Workforce/staff attendance architecture

### 6.1 New service

Create `services/workforce-service` with:

- Port 8083
- PostgreSQL database `workforce_service_db`
- User `workforce_service_user`
- Password environment variable `WORKFORCE_SERVICE_DB_PASSWORD`
- Flyway migrations starting at V1
- JWT resource-server security
- Tenant claim enforcement
- Actuator health/info
- Internal identity and school-timezone clients

Codex must not create the actual local database/user or start the service; it should document setup commands.

### 6.2 Identity eligibility

Identity-service should expose a narrow internal batch endpoint that confirms users are active employees of the organization.

- Maximum 100 IDs
- Reject duplicates
- Preserve order
- Return safe missing/inactive/wrong-role reasons
- Include teachers and valid school staff roles
- Exclude students and parents
- Do not expose unnecessary personal data

### 6.3 Staff policy and shifts

Initial policy supports:

- Expected check-in and check-out
- Grace minutes
- Minimum full-day working minutes
- Half-day threshold
- Missing-punch behavior
- Manual-entry permission
- Weekly offs and academic/school holidays

Complex rotating and overnight shifts may be deferred only if explicitly documented; the schema should not make later extension impossible.

### 6.4 Device security and privacy

Supported device categories:

- Fingerprint terminal
- RFID/card reader
- Face terminal
- Other punch device

Dawnrise must never store fingerprint templates, fingerprint images, face images or raw biometric material.

Store only:

- Device identity/code, vendor/type/name/location/status
- Hashed credential or protected credential reference
- External device-user code to Dawnrise user mapping
- Normalized punch events
- Allowlisted, size-limited vendor metadata where essential

Machine authentication must be separate from user JWT authentication, use constant-time credential verification, and be designed for secret rotation and rate limiting.

### 6.5 Punch ingestion

Provide generic REST batch and legacy CSV ingestion:

- Immutable external event ID
- Payload hash and idempotency
- Same ID/same payload is replay-safe
- Same ID/different payload is `409`
- Preserve `occurredAt` separately from `receivedAt`
- Resolve school-local date using school-service timezone
- Accept delayed/offline and out-of-order uploads
- Enforce batch/payload limits
- Unknown device-user mappings are quarantined/reviewable, not silently assigned
- Vendor-specific SDK/polling adapters remain deferred until an actual machine protocol is selected

### 6.6 Staff daily calculation

Derive deterministic daily records without deleting source punches.

Statuses:

- `PRESENT`
- `ABSENT`
- `LATE`
- `HALF_DAY`
- `ON_LEAVE`
- `HOLIDAY`
- `WEEKLY_OFF`
- `MISSING_PUNCH`

Rules:

- Determine first/last valid punch and working duration
- Apply grace and duration policies
- Do not mark absence before the school-local day is complete
- Recalculate idempotently after delayed events
- Preserve calculation version/audit metadata
- Authorized HR/leadership may enter manual attendance when hardware is unavailable
- Manual entries require reasons

### 6.7 Staff corrections and reports

- Request/approve/reject/cancel correction workflow
- Requester/reviewer separation
- Never delete source device events
- Immutable before/after history
- Daily register
- Employee history
- Monthly summary
- Late arrivals
- Missing punches
- Safe CSV export

Suggested permissions:

- `STAFF_ATTENDANCE_VIEW`
- `STAFF_ATTENDANCE_RECORD_MANUAL`
- `STAFF_ATTENDANCE_DEVICE_MANAGE`
- `STAFF_ATTENDANCE_IMPORT`
- `STAFF_ATTENDANCE_CORRECTION_REQUEST`
- `STAFF_ATTENDANCE_CORRECTION_APPROVE`
- `STAFF_ATTENDANCE_REPORT_VIEW`

Use conservative mappings centered on HR, ADMIN and PRINCIPAL. Device management and approval must not be broadly granted.

## 7. Cross-cutting engineering requirements

- Tenant-safe composite keys and repository predicates
- Database constraints as final integrity boundary
- No unbounded queries or unbounded uploads
- Pagination for large reads
- Idempotency for sync/import/device events
- Optimistic locking for editable state
- Pessimistic/atomic locking for submission, approval and calculation races
- External network calls should not be made while holding broad database locks
- One dependency failure must not roll back unrelated organizations' scheduled work
- Safe `400`, `401`, `403`, `404`, `409`, and `503` responses
- Generic `500` responses without internal details
- Never log secrets, raw vendor payloads, biometric data or unnecessary personal data
- Inject `Clock` for deterministic timezone tests
- No blocking sleeps in tests
- No distributed transaction
- Transactional outbox for cross-service effects

## 8. Migration discipline

Before creating a migration, inspect actual latest versions. Never reuse a version number.

For every new migration:

1. Add migration-focused automated tests.
2. Run service tests.
3. Review SQL manually.
4. Dry-run inside PostgreSQL `BEGIN`/`ROLLBACK` when practical.
5. Apply only by starting the owning service through Flyway.
6. Verify `flyway_schema_history.success = TRUE`.
7. Treat it as immutable thereafter.

## 9. Testing requirements

Every phase requires focused tests for business rules, authorization, tenant isolation, lifecycle, versions, concurrency and database constraints.

Run the owning service's full suite once after focused tests pass. Avoid overlapping Maven processes. If a suite stalls, identify the exact test rather than rerunning multiple suites concurrently.

Expected final suites:

- `academic-service`: all student attendance and existing academic tests
- `identity-service`: permissions and internal eligibility/security
- `workforce-service`: migrations, devices, ingestion, calculation, corrections, reports and context loading
- Frontend: unit/component tests and end-to-end attendance flows

## 10. Codex execution protocol

When instructed to “continue attendance implementation,” Codex should:

1. Read this complete document.
2. Inspect branch, Git status, applied Flyway state and existing implementation.
3. Select the earliest incomplete phase.
4. Implement only that coherent phase unless explicitly asked to combine phases.
5. Do not modify applied migrations.
6. Do not start services, apply migrations, create databases, commit, push, merge or switch branches unless explicitly authorized.
7. Do not touch the obsolete Desktop clone.
8. Run focused tests, then the appropriate full suite once.
9. Run `git diff --check`.
10. Report files, migrations, APIs, permissions, decisions, test counts, manual steps and the next phase.

If the working tree contains changes, Codex must determine whether they are the expected current-phase checkpoint. A `git status --short --branch` line beginning with `##` is only the branch header and does not itself mean the tree is dirty.

## 11. Definition of attendance completion

Attendance is complete when:

- Student online, deferred, offline-sync and import workflows function safely
- Submitted student attendance has audited corrections
- Student reports and correctly scoped student/parent views exist
- Submission/correction events reach a durable outbox
- Student attendance frontend works online and offline
- Workforce service handles manual and device-driven staff attendance
- Generic delayed/offline device ingestion is idempotent
- Staff calculation, corrections and reports are available
- Staff/device frontend is integrated
- Cross-service, tenant, security, concurrency and migration tests pass

Vendor-specific device adapters are not part of generic completion because they require the selected vendor protocol. The adapter interface and generic REST/CSV ingestion are required; an adapter is added after hardware selection.
