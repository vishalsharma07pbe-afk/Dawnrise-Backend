package com.dawnrise.academic.subject.entity;

import com.dawnrise.academic.subject.exception.InvalidSubjectException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectTest {

    @Test
    void valuesAreNormalized() {
        Subject subject = new Subject(
                10L,
                20L,
                " math ",
                " Mathematics ",
                " Core subject "
        );

        assertThat(subject.getCode()).isEqualTo("MATH");
        assertThat(subject.getName()).isEqualTo("Mathematics");
        assertThat(subject.getDescription()).isEqualTo("Core subject");
    }

    @Test
    void nullDescriptionIsAllowed() {
        Subject subject = subject(10L, 20L, "MATH", "Mathematics", null);

        assertThat(subject.getDescription()).isNull();
    }

    @Test
    void invalidParentIdsAreRejected() {
        assertThatThrownBy(() -> subject(0L, 20L, "MATH", "Mathematics", null))
                .isInstanceOf(InvalidSubjectException.class)
                .hasMessage("Organization ID must be greater than zero");

        assertThatThrownBy(() -> subject(10L, 0L, "MATH", "Mathematics", null))
                .isInstanceOf(InvalidSubjectException.class)
                .hasMessage("Academic year ID must be greater than zero");
    }

    @Test
    void requiredAndLengthLimitedValuesAreRejected() {
        assertThatThrownBy(() -> subject(10L, 20L, " ", "Mathematics", null))
                .isInstanceOf(InvalidSubjectException.class)
                .hasMessage("Subject code is required");

        assertThatThrownBy(() -> subject(10L, 20L, "MATH", " ", null))
                .isInstanceOf(InvalidSubjectException.class)
                .hasMessage("Subject name is required");

        assertThatThrownBy(() -> subject(10L, 20L, "a".repeat(51), "Mathematics", null))
                .isInstanceOf(InvalidSubjectException.class)
                .hasMessage("Subject code cannot exceed 50 characters");

        assertThatThrownBy(() -> subject(10L, 20L, "MATH", "a".repeat(121), null))
                .isInstanceOf(InvalidSubjectException.class)
                .hasMessage("Subject name cannot exceed 120 characters");
    }

    @Test
    void invalidDescriptionIsRejected() {
        assertThatThrownBy(() -> subject(10L, 20L, "MATH", "Mathematics", " "))
                .isInstanceOf(InvalidSubjectException.class)
                .hasMessage("Subject description cannot be blank");

        assertThatThrownBy(() -> subject(10L, 20L, "MATH", "Mathematics", "a".repeat(501)))
                .isInstanceOf(InvalidSubjectException.class)
                .hasMessage("Subject description cannot exceed 500 characters");
    }

    @Test
    void updateDetailsNormalizesAndValidatesValues() {
        Subject subject = subject(10L, 20L, "MATH", "Mathematics", null);

        subject.updateDetails(" sci ", " Science ", " Natural sciences ");

        assertThat(subject.getCode()).isEqualTo("SCI");
        assertThat(subject.getName()).isEqualTo("Science");
        assertThat(subject.getDescription()).isEqualTo("Natural sciences");

        assertThatThrownBy(() -> subject.updateDetails(" ", "Science", null))
                .isInstanceOf(InvalidSubjectException.class)
                .hasMessage("Subject code is required");
    }

    private static Subject subject(
            Long organizationId,
            Long academicYearId,
            String code,
            String name,
            String description
    ) {
        return new Subject(
                organizationId,
                academicYearId,
                code,
                name,
                description
        );
    }
}
