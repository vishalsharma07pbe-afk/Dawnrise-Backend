ALTER TABLE student_attendance_policies
    ADD COLUMN deferred_entry_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN teacher_back_entry_days INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN leadership_back_entry_days INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN automatic_submission_enabled BOOLEAN NOT NULL DEFAULT FALSE;


/*
 * Teachers cannot receive a larger back-entry window than leadership.
 */
ALTER TABLE student_attendance_policies
    ADD CONSTRAINT chk_student_attendance_policy_back_entry_days
        CHECK (
            teacher_back_entry_days >= 0
                AND teacher_back_entry_days <= 365
                AND leadership_back_entry_days >= teacher_back_entry_days
                AND leadership_back_entry_days <= 365
            );


/*
 * Defaults above are only used to backfill existing policy rows.
 * Future inserts must explicitly provide these policy values.
 */
ALTER TABLE student_attendance_policies
    ALTER COLUMN deferred_entry_enabled DROP DEFAULT,
    ALTER COLUMN teacher_back_entry_days DROP DEFAULT,
    ALTER COLUMN leadership_back_entry_days DROP DEFAULT,
    ALTER COLUMN automatic_submission_enabled DROP DEFAULT;