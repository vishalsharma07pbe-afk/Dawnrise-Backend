ALTER TABLE school_notification_outbox DROP CONSTRAINT school_notification_outbox_source_type_check;
ALTER TABLE school_notification_outbox ADD CONSTRAINT school_notification_outbox_source_type_check CHECK(source_type IN('ANNOUNCEMENT','RELEASE','LEAVE','POLICY','CONVERSATION'));
ALTER TABLE school_notification_outbox DROP CONSTRAINT school_notification_outbox_event_type_check;
ALTER TABLE school_notification_outbox ADD CONSTRAINT school_notification_outbox_event_type_check CHECK(event_type IN ('SCHOOL_ANNOUNCEMENT','DAWNRISE_RELEASE','LEAVE_REQUESTED','LEAVE_APPROVAL_REQUIRED','LEAVE_APPROVED','LEAVE_REJECTED','LEAVE_WITHDRAWN','LEAVE_CANCELLED','LEAVE_CHANGED','LEAVE_POLICY_CREATED','LEAVE_POLICY_RETIRED','PARENT_COMMUNICATION_MESSAGE'));
