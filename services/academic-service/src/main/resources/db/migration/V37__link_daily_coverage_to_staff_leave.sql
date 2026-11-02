ALTER TABLE timetable_teacher_absences
    ADD COLUMN source_reference VARCHAR(180),
    ADD CONSTRAINT uk_timetable_teacher_absence_source_reference UNIQUE(organization_id,source_reference);
