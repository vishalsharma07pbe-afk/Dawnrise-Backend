/*
 * A teacher may teach multiple subjects, including in their class-teacher
 * section, but may own only one class-teacher responsibility per academic year.
 */
CREATE UNIQUE INDEX uk_teacher_assignment_one_class_per_teacher
    ON teacher_assignments (
                            organization_id,
                            academic_year_id,
                            teacher_user_id
        )
    WHERE assignment_type = 'CLASS_TEACHER';
