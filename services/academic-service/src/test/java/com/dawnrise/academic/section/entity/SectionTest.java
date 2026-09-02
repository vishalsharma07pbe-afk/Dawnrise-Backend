package com.dawnrise.academic.section.entity;

import com.dawnrise.academic.section.exception.InvalidSectionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SectionTest {

    @Test
    void valuesAreNormalized() {
        Section section = new Section(
                10L,
                20L,
                30L,
                " a ",
                " Section A ",
                1
        );

        assertThat(section.getCode()).isEqualTo("A");
        assertThat(section.getName()).isEqualTo("Section A");
        assertThat(section.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void invalidParentIdsAreRejected() {
        assertThatThrownBy(() -> section(0L, 20L, 30L, "A", "Section A", 1))
                .isInstanceOf(InvalidSectionException.class)
                .hasMessage("Organization ID must be greater than zero");

        assertThatThrownBy(() -> section(10L, 0L, 30L, "A", "Section A", 1))
                .isInstanceOf(InvalidSectionException.class)
                .hasMessage("Academic year ID must be greater than zero");

        assertThatThrownBy(() -> section(10L, 20L, 0L, "A", "Section A", 1))
                .isInstanceOf(InvalidSectionException.class)
                .hasMessage("Grade level ID must be greater than zero");
    }

    @Test
    void requiredAndLengthLimitedValuesAreRejected() {
        assertThatThrownBy(() -> section(10L, 20L, 30L, " ", "Section A", 1))
                .isInstanceOf(InvalidSectionException.class)
                .hasMessage("Section code is required");

        assertThatThrownBy(() -> section(10L, 20L, 30L, "A", " ", 1))
                .isInstanceOf(InvalidSectionException.class)
                .hasMessage("Section name is required");

        assertThatThrownBy(() -> section(10L, 20L, 30L, "a".repeat(51), "Section A", 1))
                .isInstanceOf(InvalidSectionException.class)
                .hasMessage("Section code cannot exceed 50 characters");

        assertThatThrownBy(() -> section(10L, 20L, 30L, "A", "a".repeat(101), 1))
                .isInstanceOf(InvalidSectionException.class)
                .hasMessage("Section name cannot exceed 100 characters");
    }

    @Test
    void displayOrderMustBeGreaterThanZero() {
        assertThatThrownBy(() -> section(10L, 20L, 30L, "A", "Section A", 0))
                .isInstanceOf(InvalidSectionException.class)
                .hasMessage("Display order must be greater than zero");
    }

    @Test
    void updateDetailsNormalizesValues() {
        Section section = section(10L, 20L, 30L, "A", "Section A", 1);

        section.updateDetails(" b ", " Section B ", 2);

        assertThat(section.getCode()).isEqualTo("B");
        assertThat(section.getName()).isEqualTo("Section B");
        assertThat(section.getDisplayOrder()).isEqualTo(2);
    }

    private static Section section(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            String code,
            String name,
            Integer displayOrder
    ) {
        return new Section(
                organizationId,
                academicYearId,
                gradeLevelId,
                code,
                name,
                displayOrder
        );
    }
}
