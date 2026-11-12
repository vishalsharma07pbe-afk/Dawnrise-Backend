ALTER TABLE parent_communication_threads
    ADD COLUMN thread_kind VARCHAR(16) NOT NULL DEFAULT 'PARENT'
        CHECK (thread_kind IN ('PARENT','STAFF_DIRECT'));

ALTER TABLE parent_communication_threads DROP CONSTRAINT parent_communication_threads_contact_kind_check;
ALTER TABLE parent_communication_threads
    ADD CONSTRAINT parent_communication_threads_contact_kind_check
        CHECK (contact_kind IN ('ASSIGNED_TEACHER','CONFIGURED_STAFF','ACADEMIC_HEAD','PRINCIPAL','DIRECT_STAFF'));

ALTER TABLE parent_communication_participants DROP CONSTRAINT parent_communication_participants_participant_role_check;
ALTER TABLE parent_communication_participants
    ADD CONSTRAINT parent_communication_participants_participant_role_check
        CHECK (participant_role IN ('GUARDIAN','CONTACT','HEAD','PRINCIPAL','INITIATOR','RECIPIENT'));

CREATE UNIQUE INDEX uk_staff_direct_open_issue
    ON parent_communication_threads(organization_id,guardian_user_id,contact_user_id,subject)
    WHERE status='OPEN' AND thread_kind='STAFF_DIRECT';
