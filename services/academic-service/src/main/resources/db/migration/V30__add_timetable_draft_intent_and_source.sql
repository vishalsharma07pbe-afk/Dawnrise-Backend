ALTER TABLE timetable_drafts
    ADD COLUMN effective_from_intent DATE,
    ADD COLUMN effective_to_exclusive_intent DATE,
    ADD CONSTRAINT chk_timetable_draft_effective_intent CHECK (
        effective_to_exclusive_intent IS NULL
        OR (effective_from_intent IS NOT NULL AND effective_to_exclusive_intent > effective_from_intent)
    );

CREATE INDEX idx_timetable_drafts_staff_list
    ON timetable_drafts (organization_id, academic_year_id, updated_at DESC, id DESC);

CREATE INDEX idx_timetable_generation_proposal_draft_lookup
    ON timetable_generation_proposals (organization_id, academic_year_id, draft_id)
    WHERE draft_id IS NOT NULL;

ALTER TABLE timetable_draft_audit DROP CONSTRAINT chk_timetable_draft_audit_action;
ALTER TABLE timetable_draft_audit ADD CONSTRAINT chk_timetable_draft_audit_action CHECK
    (action IN ('CREATE_DRAFT','ADD','MOVE','SWAP','REMOVE','PIN','UNPIN',
                'SUBMIT','RETURN','APPROVE','PUBLISH','UPDATE_INTENT'));
