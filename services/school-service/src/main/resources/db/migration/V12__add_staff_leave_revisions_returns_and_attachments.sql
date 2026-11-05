ALTER TABLE staff_leave_requests ADD COLUMN current_revision INTEGER NOT NULL DEFAULT 1;
ALTER TABLE staff_leave_requests ADD COLUMN returned_by_user_id BIGINT;
ALTER TABLE staff_leave_requests ADD COLUMN return_comment VARCHAR(1000);
ALTER TABLE staff_leave_requests DROP CONSTRAINT staff_leave_requests_status_check;
ALTER TABLE staff_leave_requests ADD CONSTRAINT chk_staff_leave_request_status CHECK(status IN
 ('DRAFT','PENDING','PENDING_CORRECTION','APPROVED','REJECTED','WITHDRAWN','CANCELLED','PARTIALLY_CANCELLED'));

CREATE TABLE staff_leave_revisions (
 id BIGSERIAL PRIMARY KEY,organization_id BIGINT NOT NULL,leave_request_id BIGINT NOT NULL,
 revision_number INTEGER NOT NULL,leave_type_id BIGINT NOT NULL,request_kind VARCHAR(30) NOT NULL,
 start_date DATE NOT NULL,end_date DATE NOT NULL,start_time TIME,end_time TIME,half_day_period VARCHAR(20),
 units NUMERIC(8,2) NOT NULL,reason VARCHAR(2000) NOT NULL,created_by_user_id BIGINT NOT NULL,
 superseded BOOLEAN NOT NULL DEFAULT FALSE,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_staff_leave_revision_request FOREIGN KEY(leave_request_id,organization_id) REFERENCES staff_leave_requests(id,organization_id),
 CONSTRAINT uk_staff_leave_revision UNIQUE(leave_request_id,revision_number)
);

INSERT INTO staff_leave_revisions(organization_id,leave_request_id,revision_number,leave_type_id,request_kind,start_date,end_date,start_time,end_time,half_day_period,units,reason,created_by_user_id)
SELECT organization_id,id,1,leave_type_id,request_kind,start_date,end_date,start_time,end_time,half_day_period,units,reason,requester_user_id FROM staff_leave_requests;

ALTER TABLE staff_leave_approvals ADD COLUMN revision_number INTEGER NOT NULL DEFAULT 1;
ALTER TABLE staff_leave_approvals ADD COLUMN decision_key UUID;
ALTER TABLE staff_leave_approvals DROP CONSTRAINT staff_leave_approvals_status_check;
ALTER TABLE staff_leave_approvals ADD CONSTRAINT chk_staff_leave_approval_status CHECK(status IN ('WAITING','PENDING','APPROVED','REJECTED','RETURNED','SUPERSEDED','CANCELLED'));
ALTER TABLE staff_leave_approvals DROP CONSTRAINT uk_staff_leave_approval_step;
ALTER TABLE staff_leave_approvals ADD CONSTRAINT uk_staff_leave_approval_revision_step UNIQUE(leave_request_id,revision_number,step_number);
ALTER TABLE staff_leave_approvals ADD CONSTRAINT uk_staff_leave_decision_key UNIQUE NULLS DISTINCT(organization_id,decision_key);

CREATE TABLE staff_leave_attachments (
 id UUID PRIMARY KEY,organization_id BIGINT NOT NULL,leave_request_id BIGINT NOT NULL,revision_number INTEGER NOT NULL,
 original_name VARCHAR(200) NOT NULL,content_type VARCHAR(80) NOT NULL,byte_size BIGINT NOT NULL,
 sha256 CHAR(64) NOT NULL,content BYTEA NOT NULL,uploaded_by_user_id BIGINT NOT NULL,
 removed_at TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_staff_leave_attachment_request FOREIGN KEY(leave_request_id,organization_id) REFERENCES staff_leave_requests(id,organization_id),
 CONSTRAINT chk_staff_leave_attachment_size CHECK(byte_size BETWEEN 1 AND 10485760),
 CONSTRAINT chk_staff_leave_attachment_type CHECK(content_type IN ('application/pdf','image/jpeg','image/png'))
);

CREATE TABLE staff_leave_approver_mappings (
 id BIGSERIAL PRIMARY KEY,organization_id BIGINT NOT NULL,staff_category VARCHAR(40) NOT NULL,
 stage VARCHAR(40) NOT NULL,subject_user_id BIGINT,approver_user_id BIGINT NOT NULL,active BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_staff_leave_approver_mapping UNIQUE NULLS NOT DISTINCT(organization_id,staff_category,stage,subject_user_id),
 CONSTRAINT chk_staff_leave_category CHECK(staff_category IN ('TEACHING','NON_TEACHING')),
 CONSTRAINT chk_staff_leave_mapping_stage CHECK(stage IN ('REPORTING_HEAD','ACADEMIC_HEAD','PRINCIPAL','HR','ADMINISTRATIVE_AUTHORITY'))
);

CREATE INDEX idx_staff_leave_attachment_owner ON staff_leave_attachments(organization_id,leave_request_id,revision_number) WHERE removed_at IS NULL;
