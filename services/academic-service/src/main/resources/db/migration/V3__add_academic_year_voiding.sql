/*
 * VOIDED represents an academic year that was created incorrectly.
 * The record remains for audit history, but its name can be reused.
 */

ALTER TABLE academic_years
    ADD COLUMN void_reason VARCHAR(500),
    ADD COLUMN voided_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN voided_by_user_id BIGINT;


/*
 * Extend the existing lifecycle constraint.
 */

ALTER TABLE academic_years
    DROP CONSTRAINT chk_academic_year_status;

ALTER TABLE academic_years
    ADD CONSTRAINT chk_academic_year_status
        CHECK (
            status IN (
                       'PLANNED',
                       'ACTIVE',
                       'CLOSED',
                       'VOIDED'
                )
            );


/*
 * A voided year must contain complete audit information.
 * Other statuses must not contain voiding information.
 */

ALTER TABLE academic_years
    ADD CONSTRAINT chk_academic_year_void_metadata
        CHECK (
            (
                status = 'VOIDED'
                    AND void_reason IS NOT NULL
                    AND BTRIM(void_reason) <> ''
                    AND voided_at IS NOT NULL
                    AND voided_by_user_id IS NOT NULL
                    AND voided_by_user_id > 0
                )
                OR
            (
                status <> 'VOIDED'
                    AND void_reason IS NULL
                    AND voided_at IS NULL
                    AND voided_by_user_id IS NULL
                )
            );


/*
 * Remove the original uniqueness rule. It included VOIDED records,
 * which prevented creation of a corrected academic year.
 */

ALTER TABLE academic_years
    DROP CONSTRAINT uk_academic_year_organization_name;


/*
 * Names remain unique among usable academic years.
 * A name belonging only to a VOIDED record can be reused.
 *
 * LOWER(name) also prevents case-only duplicates such as:
 * "Academic Year 2026-2027" and "ACADEMIC YEAR 2026-2027".
 */

CREATE UNIQUE INDEX uk_academic_year_organization_name_non_voided
    ON academic_years (
                       organization_id,
                       LOWER(name)
        )
    WHERE status <> 'VOIDED';