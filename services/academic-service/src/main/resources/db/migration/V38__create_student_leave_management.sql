CREATE TABLE student_leave_policies (
    id BIGSERIAL PRIMARY KEY, organization_id BIGINT NOT NULL,
    long_leave_threshold_days INTEGER NOT NULL DEFAULT 10,
    partial_day_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE, version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_student_leave_policy_org UNIQUE(organization_id),
    CONSTRAINT chk_student_leave_threshold CHECK(long_leave_threshold_days BETWEEN 1 AND 365)
);

CREATE TABLE student_leave_approver_mappings (
    id BIGSERIAL PRIMARY KEY, organization_id BIGINT NOT NULL,
    stage VARCHAR(40) NOT NULL, academic_year_id BIGINT,
    grade_level_id BIGINT, section_id BIGINT, approver_user_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_student_leave_approver_scope UNIQUE NULLS NOT DISTINCT
      (organization_id,stage,academic_year_id,grade_level_id,section_id),
    CONSTRAINT chk_student_leave_stage CHECK(stage IN ('ACADEMIC_HEAD','PRINCIPAL'))
);

CREATE TABLE student_leave_requests (
    id BIGSERIAL PRIMARY KEY, organization_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL, initiator_user_id BIGINT NOT NULL,
    initiator_type VARCHAR(20) NOT NULL, academic_year_id BIGINT NOT NULL,
    grade_level_id BIGINT NOT NULL, section_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT', current_revision INTEGER NOT NULL DEFAULT 1,
    current_stage VARCHAR(40), working_days NUMERIC(7,2) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0, submitted_at TIMESTAMPTZ,
    decided_at TIMESTAMPTZ, cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_student_leave_request_tenant UNIQUE(id,organization_id),
    CONSTRAINT chk_student_leave_initiator CHECK(initiator_type IN ('STUDENT','GUARDIAN')),
    CONSTRAINT chk_student_leave_status CHECK(status IN ('DRAFT','PENDING_APPROVAL','PENDING_CORRECTION','APPROVED','REJECTED','WITHDRAWN','CANCELLED','PARTIALLY_CANCELLED'))
);

CREATE TABLE student_leave_revisions (
    id BIGSERIAL PRIMARY KEY, organization_id BIGINT NOT NULL,
    request_id BIGINT NOT NULL, revision_number INTEGER NOT NULL,
    start_date DATE NOT NULL, end_date DATE NOT NULL,
    partial_day VARCHAR(20), reason VARCHAR(2000) NOT NULL,
    working_days NUMERIC(7,2) NOT NULL, superseded BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_user_id BIGINT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_leave_revision_request FOREIGN KEY(request_id,organization_id) REFERENCES student_leave_requests(id,organization_id),
    CONSTRAINT uk_student_leave_revision UNIQUE(request_id,revision_number),
    CONSTRAINT chk_student_leave_revision_dates CHECK(end_date>=start_date),
    CONSTRAINT chk_student_leave_partial CHECK(partial_day IS NULL OR partial_day IN ('MORNING','AFTERNOON'))
);

CREATE TABLE student_leave_approvals (
    id BIGSERIAL PRIMARY KEY, organization_id BIGINT NOT NULL,
    request_id BIGINT NOT NULL, revision_number INTEGER NOT NULL,
    step_number INTEGER NOT NULL, stage VARCHAR(40) NOT NULL,
    approver_user_id BIGINT, status VARCHAR(20) NOT NULL,
    decision_reason VARCHAR(1000), decision_key UUID,
    decided_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_student_leave_approval_request FOREIGN KEY(request_id,organization_id) REFERENCES student_leave_requests(id,organization_id),
    CONSTRAINT uk_student_leave_approval_step UNIQUE(request_id,revision_number,step_number),
    CONSTRAINT uk_student_leave_decision_key UNIQUE NULLS DISTINCT(organization_id,decision_key),
    CONSTRAINT chk_student_leave_approval_status CHECK(status IN ('WAITING','PENDING','APPROVED','REJECTED','RETURNED','SUPERSEDED','CANCELLED'))
);

CREATE TABLE student_leave_attachments (
    id UUID PRIMARY KEY, organization_id BIGINT NOT NULL, request_id BIGINT NOT NULL,
    revision_number INTEGER NOT NULL, original_name VARCHAR(200) NOT NULL,
    content_type VARCHAR(80) NOT NULL, byte_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL, content BYTEA NOT NULL, uploaded_by_user_id BIGINT NOT NULL,
    removed_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_leave_attachment_request FOREIGN KEY(request_id,organization_id) REFERENCES student_leave_requests(id,organization_id),
    CONSTRAINT chk_student_leave_attachment_size CHECK(byte_size BETWEEN 1 AND 10485760),
    CONSTRAINT chk_student_leave_attachment_type CHECK(content_type IN ('application/pdf','image/jpeg','image/png'))
);

CREATE TABLE student_leave_audit (
    id BIGSERIAL PRIMARY KEY, organization_id BIGINT NOT NULL, request_id BIGINT NOT NULL,
    revision_number INTEGER, actor_user_id BIGINT NOT NULL, action VARCHAR(60) NOT NULL,
    detail VARCHAR(1000), occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_leave_audit_request FOREIGN KEY(request_id,organization_id) REFERENCES student_leave_requests(id,organization_id)
);

CREATE TABLE student_leave_attendance_projection (
    id BIGSERIAL PRIMARY KEY, organization_id BIGINT NOT NULL, request_id BIGINT NOT NULL,
    revision_number INTEGER NOT NULL, attendance_date DATE NOT NULL,
    projected_status VARCHAR(30) NOT NULL, conflict_status VARCHAR(30) NOT NULL DEFAULT 'NONE',
    resolved_by_user_id BIGINT, resolved_at TIMESTAMPTZ,
    CONSTRAINT fk_student_leave_projection_request FOREIGN KEY(request_id,organization_id) REFERENCES student_leave_requests(id,organization_id),
    CONSTRAINT uk_student_leave_projection UNIQUE(request_id,revision_number,attendance_date),
    CONSTRAINT chk_student_leave_projection_status CHECK(projected_status IN ('EXCUSED_LEAVE','HALF_DAY_EXCUSED')),
    CONSTRAINT chk_student_leave_conflict CHECK(conflict_status IN ('NONE','PRESENT_RECORDED','PARTIAL_RECORDED','LOCKED_REGISTER','RESOLVED'))
);

CREATE TABLE student_leave_notification_outbox (
 id BIGSERIAL PRIMARY KEY,event_id UUID NOT NULL UNIQUE,organization_id BIGINT NOT NULL,
 request_id BIGINT NOT NULL,revision_number INTEGER NOT NULL,event_type VARCHAR(60) NOT NULL,
 student_user_id BIGINT NOT NULL,recipient_user_ids BIGINT[] NOT NULL,payload JSONB NOT NULL,
 idempotency_key VARCHAR(200) NOT NULL,publication_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 attempt_count INTEGER NOT NULL DEFAULT 0,available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 lease_owner UUID,lease_until TIMESTAMPTZ,published_at TIMESTAMPTZ,dead_lettered_at TIMESTAMPTZ,
 last_error VARCHAR(1000),occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_student_leave_outbox_request FOREIGN KEY(request_id,organization_id) REFERENCES student_leave_requests(id,organization_id),
 CONSTRAINT uk_student_leave_outbox_key UNIQUE(organization_id,idempotency_key),
 CONSTRAINT chk_student_leave_outbox_recipients CHECK(CARDINALITY(recipient_user_ids)>0 AND 0<ALL(recipient_user_ids)),
 CONSTRAINT chk_student_leave_outbox_status CHECK(publication_status IN ('PENDING','PUBLISHED','DEAD_LETTER')),
 CONSTRAINT chk_student_leave_outbox_lease CHECK((lease_owner IS NULL)=(lease_until IS NULL))
);

CREATE INDEX idx_student_leave_self ON student_leave_requests(organization_id,student_user_id,created_at DESC);
CREATE INDEX idx_student_leave_initiator ON student_leave_requests(organization_id,initiator_user_id,created_at DESC);
CREATE INDEX idx_student_leave_queue ON student_leave_approvals(organization_id,approver_user_id,status);
CREATE INDEX idx_student_leave_outbox_claim ON student_leave_notification_outbox(publication_status,available_at,lease_until,id) WHERE publication_status='PENDING';
CREATE UNIQUE INDEX uk_student_leave_active_overlap_guard ON student_leave_requests(organization_id,student_user_id,id) WHERE status IN ('PENDING_APPROVAL','PENDING_CORRECTION','APPROVED','PARTIALLY_CANCELLED');

CREATE FUNCTION prevent_student_leave_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'student leave history is immutable'; END $$;
CREATE TRIGGER trg_student_leave_audit_immutable BEFORE UPDATE OR DELETE ON student_leave_audit FOR EACH ROW EXECUTE FUNCTION prevent_student_leave_history_mutation();
