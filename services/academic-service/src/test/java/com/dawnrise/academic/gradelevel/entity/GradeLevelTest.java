package com.dawnrise.academic.gradelevel.entity;

import com.dawnrise.academic.gradelevel.exception.InvalidGradeLevelException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradeLevelTest {

    @Test
    void valuesAreNormalized() {
        GradeLevel gradeLevel = new GradeLevel(
                10L,
                20L,
                " kg-1 ",
                " Kindergarten 1 ",
                1
        );

        assertThat(gradeLevel.getCode()).isEqualTo("KG-1");
        assertThat(gradeLevel.getName()).isEqualTo("Kindergarten 1");
        assertThat(gradeLevel.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void invalidIdentifiersAreRejected() {
        assertThatThrownBy(() -> new GradeLevel(
                0L,
                20L,
                "G1",
                "Grade 1",
                1
        )).isInstanceOf(InvalidGradeLevelException.class)
                .hasMessage("Organization ID must be greater than zero");

        assertThatThrownBy(() -> new GradeLevel(
                10L,
                0L,
                "G1",
                "Grade 1",
                1
        )).isInstanceOf(InvalidGradeLevelException.class)
                .hasMessage("Academic year ID must be greater than zero");
    }

    @Test
    void invalidDetailsAreRejected() {
        assertThatThrownBy(() -> gradeLevel(" ", "Grade 1", 1))
                .isInstanceOf(InvalidGradeLevelException.class)
                .hasMessage("Grade level code is required");

        assertThatThrownBy(() -> gradeLevel("G 1", "Grade 1", 1))
                .isInstanceOf(InvalidGradeLevelException.class)
                .hasMessage("Grade level code can contain only letters, numbers, hyphens, and underscores");

        assertThatThrownBy(() -> gradeLevel("G1", " ", 1))
                .isInstanceOf(InvalidGradeLevelException.class)
                .hasMessage("Grade level name is required");

        assertThatThrownBy(() -> gradeLevel("G1", "Grade 1", 0))
                .isInstanceOf(InvalidGradeLevelException.class)
                .hasMessage("Display order must be greater than zero");
    }

    @Test
    void updateDetailsNormalizesValues() {
        GradeLevel gradeLevel = gradeLevel("G1", "Grade 1", 1);

        gradeLevel.updateDetails(" g2 ", " Grade 2 ", 2);

        assertThat(gradeLevel.getCode()).isEqualTo("G2");
        assertThat(gradeLevel.getName()).isEqualTo("Grade 2");
        assertThat(gradeLevel.getDisplayOrder()).isEqualTo(2);
    }

    private static GradeLevel gradeLevel(
            String code,
            String name,
            Integer displayOrder
    ) {
        return new GradeLevel(10L, 20L, code, name, displayOrder);
    }
}
