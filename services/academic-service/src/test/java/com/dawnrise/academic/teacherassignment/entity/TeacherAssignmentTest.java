package com.dawnrise.academic.teacherassignment.entity;

import com.dawnrise.academic.teacherassignment.enums.TeacherAssignmentType;
import com.dawnrise.academic.teacherassignment.exception.InvalidTeacherAssignmentException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeacherAssignmentTest {

    @Test
    void validClassTeacherWithoutGradeSubject() {
        TeacherAssignment assignment = assignment(
                10L,
                20L,
                30L,
                40L,
                null,
                50L,
                TeacherAssignmentType.CLASS_TEACHER
        );

        assertThat(assignment.getGradeLevelSubjectId()).isNull();
        assertThat(assignment.getTeacherUserId()).isEqualTo(50L);
        assertThat(assignment.getAssignmentType())
                .isEqualTo(TeacherAssignmentType.CLASS_TEACHER);
    }

    @Test
    void validSubjectTeacherWithGradeSubject() {
        TeacherAssignment assignment = assignment(
                10L,
                20L,
                30L,
                40L,
                60L,
                50L,
                TeacherAssignmentType.SUBJECT_TEACHER
        );

        assertThat(assignment.getGradeLevelSubjectId()).isEqualTo(60L);
        assertThat(assignment.getAssignmentType())
                .isEqualTo(TeacherAssignmentType.SUBJECT_TEACHER);
    }

    @Test
    void invalidParentAndTeacherIdsAreRejected() {
        assertThatThrownBy(() -> assignment(0L, 20L, 30L, 40L, null, 50L, TeacherAssignmentType.CLASS_TEACHER))
                .isInstanceOf(InvalidTeacherAssignmentException.class)
                .hasMessage("Organization ID must be greater than zero");

        assertThatThrownBy(() -> assignment(10L, 0L, 30L, 40L, null, 50L, TeacherAssignmentType.CLASS_TEACHER))
                .isInstanceOf(InvalidTeacherAssignmentException.class)
                .hasMessage("Academic year ID must be greater than zero");

        assertThatThrownBy(() -> assignment(10L, 20L, 0L, 40L, null, 50L, TeacherAssignmentType.CLASS_TEACHER))
                .isInstanceOf(InvalidTeacherAssignmentException.class)
                .hasMessage("Grade level ID must be greater than zero");

        assertThatThrownBy(() -> assignment(10L, 20L, 30L, 0L, null, 50L, TeacherAssignmentType.CLASS_TEACHER))
                .isInstanceOf(InvalidTeacherAssignmentException.class)
                .hasMessage("Section ID must be greater than zero");

        assertThatThrownBy(() -> assignment(10L, 20L, 30L, 40L, null, 0L, TeacherAssignmentType.CLASS_TEACHER))
                .isInstanceOf(InvalidTeacherAssignmentException.class)
                .hasMessage("Teacher user ID must be greater than zero");
    }

    @Test
    void assignmentTypeIsRequired() {
        assertThatThrownBy(() -> assignment(10L, 20L, 30L, 40L, null, 50L, null))
                .isInstanceOf(InvalidTeacherAssignmentException.class)
                .hasMessage("Assignment type is required");
    }

    @Test
    void classTeacherRejectsGradeSubject() {
        assertThatThrownBy(() -> assignment(
                10L,
                20L,
                30L,
                40L,
                60L,
                50L,
                TeacherAssignmentType.CLASS_TEACHER
        )).isInstanceOf(InvalidTeacherAssignmentException.class)
                .hasMessage("A class-teacher assignment cannot contain a subject");
    }

    @Test
    void subjectTeacherRequiresPositiveGradeSubject() {
        assertThatThrownBy(() -> assignment(
                10L,
                20L,
                30L,
                40L,
                null,
                50L,
                TeacherAssignmentType.SUBJECT_TEACHER
        )).isInstanceOf(InvalidTeacherAssignmentException.class)
                .hasMessage("A subject-teacher assignment requires a grade subject");

        assertThatThrownBy(() -> assignment(
                10L,
                20L,
                30L,
                40L,
                0L,
                50L,
                TeacherAssignmentType.SUBJECT_TEACHER
        )).isInstanceOf(InvalidTeacherAssignmentException.class)
                .hasMessage("A subject-teacher assignment requires a grade subject");
    }

    @Test
    void replaceTeacherValidatesTeacherUserId() {
        TeacherAssignment assignment = assignment(
                10L,
                20L,
                30L,
                40L,
                null,
                50L,
                TeacherAssignmentType.CLASS_TEACHER
        );

        assignment.replaceTeacher(51L);

        assertThat(assignment.getTeacherUserId()).isEqualTo(51L);

        assertThatThrownBy(() -> assignment.replaceTeacher(0L))
                .isInstanceOf(InvalidTeacherAssignmentException.class)
                .hasMessage("Teacher user ID must be greater than zero");
    }

    private static TeacherAssignment assignment(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            Long gradeLevelSubjectId,
            Long teacherUserId,
            TeacherAssignmentType assignmentType
    ) {
        return new TeacherAssignment(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                gradeLevelSubjectId,
                teacherUserId,
                assignmentType
        );
    }
}
