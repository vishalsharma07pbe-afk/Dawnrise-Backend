package com.dawnrise.academic.gradelevelsubject.entity;

import com.dawnrise.academic.gradelevelsubject.exception.InvalidGradeLevelSubjectException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradeLevelSubjectTest {

    @Test
    void validConstructionSetsValues() {
        GradeLevelSubject assignment = assignment(
                10L,
                20L,
                30L,
                40L,
                true,
                1
        );

        assertThat(assignment.getOrganizationId()).isEqualTo(10L);
        assertThat(assignment.getAcademicYearId()).isEqualTo(20L);
        assertThat(assignment.getGradeLevelId()).isEqualTo(30L);
        assertThat(assignment.getSubjectId()).isEqualTo(40L);
        assertThat(assignment.getMandatory()).isTrue();
        assertThat(assignment.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void invalidParentIdsAreRejected() {
        assertThatThrownBy(() -> assignment(0L, 20L, 30L, 40L, true, 1))
                .isInstanceOf(InvalidGradeLevelSubjectException.class)
                .hasMessage("Organization ID must be greater than zero");

        assertThatThrownBy(() -> assignment(10L, 0L, 30L, 40L, true, 1))
                .isInstanceOf(InvalidGradeLevelSubjectException.class)
                .hasMessage("Academic year ID must be greater than zero");

        assertThatThrownBy(() -> assignment(10L, 20L, 0L, 40L, true, 1))
                .isInstanceOf(InvalidGradeLevelSubjectException.class)
                .hasMessage("Grade level ID must be greater than zero");

        assertThatThrownBy(() -> assignment(10L, 20L, 30L, 0L, true, 1))
                .isInstanceOf(InvalidGradeLevelSubjectException.class)
                .hasMessage("Subject ID must be greater than zero");
    }

    @Test
    void mandatoryValueIsRequired() {
        assertThatThrownBy(() -> assignment(10L, 20L, 30L, 40L, null, 1))
                .isInstanceOf(InvalidGradeLevelSubjectException.class)
                .hasMessage("Mandatory value is required");
    }

    @Test
    void displayOrderMustBePositive() {
        assertThatThrownBy(() -> assignment(10L, 20L, 30L, 40L, true, 0))
                .isInstanceOf(InvalidGradeLevelSubjectException.class)
                .hasMessage("Display order must be greater than zero");
    }

    @Test
    void updateDetailsChangesMutableValuesOnly() {
        GradeLevelSubject assignment =
                assignment(10L, 20L, 30L, 40L, true, 1);

        assignment.updateDetails(false, 2);

        assertThat(assignment.getSubjectId()).isEqualTo(40L);
        assertThat(assignment.getMandatory()).isFalse();
        assertThat(assignment.getDisplayOrder()).isEqualTo(2);

        assertThatThrownBy(() -> assignment.updateDetails(null, 2))
                .isInstanceOf(InvalidGradeLevelSubjectException.class)
                .hasMessage("Mandatory value is required");
    }

    private static GradeLevelSubject assignment(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            Long subjectId,
            Boolean mandatory,
            Integer displayOrder
    ) {
        return new GradeLevelSubject(
                organizationId,
                academicYearId,
                gradeLevelId,
                subjectId,
                mandatory,
                displayOrder
        );
    }
}
