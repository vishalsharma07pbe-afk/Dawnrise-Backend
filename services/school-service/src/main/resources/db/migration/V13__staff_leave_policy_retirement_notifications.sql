ALTER TABLE staff_leave_types ADD COLUMN retired_at TIMESTAMPTZ;
ALTER TABLE staff_leave_policy_audit DROP CONSTRAINT staff_leave_policy_audit_action_check;
ALTER TABLE staff_leave_policy_audit ADD CONSTRAINT staff_leave_policy_audit_action_check CHECK(action IN ('CREATED','UPDATED','RETIRED'));
ALTER TABLE school_notification_outbox DROP CONSTRAINT school_notification_outbox_source_type_check;
ALTER TABLE school_notification_outbox ADD CONSTRAINT school_notification_outbox_source_type_check CHECK(source_type IN ('ANNOUNCEMENT','RELEASE','LEAVE','POLICY'));
ALTER TABLE school_notification_outbox DROP CONSTRAINT school_notification_outbox_event_type_check;
ALTER TABLE school_notification_outbox ADD CONSTRAINT school_notification_outbox_event_type_check CHECK(event_type IN ('SCHOOL_ANNOUNCEMENT','DAWNRISE_RELEASE','LEAVE_REQUESTED','LEAVE_APPROVAL_REQUIRED','LEAVE_APPROVED','LEAVE_REJECTED','LEAVE_WITHDRAWN','LEAVE_CANCELLED','LEAVE_CHANGED','LEAVE_POLICY_CREATED','LEAVE_POLICY_RETIRED'));
