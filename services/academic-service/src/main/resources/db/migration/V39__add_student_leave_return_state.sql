ALTER TABLE student_leave_requests
    ADD COLUMN returned_by_user_id BIGINT,
    ADD COLUMN return_comment VARCHAR(1000);

ALTER TABLE student_leave_requests
    ADD CONSTRAINT chk_student_leave_return_state
    CHECK (
        status <> 'PENDING_CORRECTION'
        OR (returned_by_user_id IS NOT NULL AND return_comment IS NOT NULL)
    );
