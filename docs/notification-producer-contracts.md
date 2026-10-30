# Notification producer contracts

This catalog is the integration boundary for Dawnrise domain modules. A producer emits only after
an authoritative state transition commits, through its local durable outbox. Identity owns inbox
state, preferences, email delivery, tenant validation, recipient deduplication, and typed actions.
Browser requests never supply notification recipients.

## Delivery rules

- `created` means published/assigned/issued, not a private draft.
- `changed` is emitted only for a material change and only to affected recipients.
- requests and approvals go to the requester, affected user, and actual current approvers.
- expiry producers emit one advance phase and one final phase with stable source-event IDs.
- cancelled, rejected, replaced, or resolved events resolve earlier actionable groups.
- urgent operational incidents remain separate from inbox read/delete state.
- students imply every active same-tenant guardian unless the event is student-private by policy.
- recipient sets are calculated from server-side roles, enrollments, assignments, and relationships.

## Connected producers

| Domain | Authoritative events | Recipient contract |
|---|---|---|
| Timetable | published, materially changed, expiring, expires today | affected students and active guardians, affected teachers, active admin/principal/vice-principal |
| Daily coverage | cover needed, approval required, confirmed substitution, imminent/class-start uncovered, changed | actual managers/approvers and assigned substitute |
| Attendance | recorded/corrected | affected student and active guardians |
| Results | result published, report card published | affected student and active guardians |
| Identity security | account locked/status changed | affected user and responsible security administrators |
| Role workflow | assignment/removal requested, approval required, approved, rejected, cancelled | affected user and actual approvers |
| School communications | announcement and Dawnrise release lifecycle | authoritative role/wing/grade/section/staff/student/guardian audience |

## Reserved integration contracts

These modules must use the same outbox and ingestion pattern when their authoritative workflow is
implemented. They must not simulate events or accept client-selected recipients meanwhile.

| Domain | Required transitions | Recipient source |
|---|---|---|
| Leave | requested, approval required, approved, rejected, cancelled | applicant, current approvers, affected scheduling owner |
| Examinations | schedule published/changed/cancelled, assessment approaching | affected sections, teachers, students, active guardians |
| Academic-year rollover | action required, approved, completed, failed | current rollover operators and school leadership |
| Enrollment/progression | enrollment confirmed/changed, progression approved/rejected | affected student, active guardians, responsible academic staff |
| Fees | invoice issued, due soon, overdue, payment confirmed/failed | financially responsible active guardians and authorized finance staff |
| Admissions | application received, action required, decision published, offer expiring | applicant contacts and assigned admissions staff |
| Documents/compliance | verification required, approved/rejected, expiring | document owner and assigned verifier/compliance owner |

Each reserved contract requires an allowlisted event type, typed action, category preference,
privacy-safe preview, grouping/resolution key, retention policy, and producer tests before activation.
