ALTER TABLE timetable_publications
    ADD COLUMN school_name VARCHAR(200);

UPDATE timetable_publications SET school_name='Dawnrise School' WHERE school_name IS NULL;

ALTER TABLE timetable_publications
    ALTER COLUMN school_name SET NOT NULL,
    ADD CONSTRAINT chk_timetable_publication_school_name CHECK (BTRIM(school_name)<>'');

ALTER TABLE timetable_publication_entries
    ADD COLUMN teacher_display_name VARCHAR(200);

UPDATE timetable_publication_entries
SET teacher_display_name='Teacher #' || teacher_user_id
WHERE teacher_user_id IS NOT NULL;

ALTER TABLE timetable_publication_entries
    ADD CONSTRAINT chk_timetable_publication_teacher_name CHECK (
        (teacher_user_id IS NULL AND teacher_display_name IS NULL) OR
        (teacher_user_id IS NOT NULL AND teacher_display_name IS NOT NULL
            AND BTRIM(teacher_display_name)<>''));
