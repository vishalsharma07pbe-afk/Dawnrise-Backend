ALTER TABLE timetable_section_subject_requirements
    ADD COLUMN allow_consecutive_periods BOOLEAN NOT NULL DEFAULT FALSE;
