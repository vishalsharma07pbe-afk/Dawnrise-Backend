ALTER TABLE student_attendance_policies
    ADD COLUMN late_penalty_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN late_occurrences_threshold INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN late_penalty_outcome VARCHAR(20) NOT NULL DEFAULT 'HALF_DAY',
    ADD COLUMN late_counting_period VARCHAR(20) NOT NULL DEFAULT 'MONTHLY';

ALTER TABLE student_attendance_policies
    ADD CONSTRAINT chk_student_attendance_policy_late_threshold
        CHECK (
            late_occurrences_threshold >= 1
            AND late_occurrences_threshold <= 100
        ),
    ADD CONSTRAINT chk_student_attendance_policy_late_outcome
        CHECK (late_penalty_outcome IN ('HALF_DAY', 'ABSENT')),
    ADD CONSTRAINT chk_student_attendance_policy_late_counting_period
        CHECK (late_counting_period IN ('MONTHLY'));

ALTER TABLE student_attendance_policies
    ALTER COLUMN late_penalty_enabled DROP DEFAULT,
    ALTER COLUMN late_occurrences_threshold DROP DEFAULT,
    ALTER COLUMN late_penalty_outcome DROP DEFAULT,
    ALTER COLUMN late_counting_period DROP DEFAULT;
