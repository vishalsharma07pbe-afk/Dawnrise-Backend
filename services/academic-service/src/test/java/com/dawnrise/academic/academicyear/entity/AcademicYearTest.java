package com.dawnrise.academic.academicyear.entity;

import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.exception.AcademicYearConflictException;
import com.dawnrise.academic.academicyear.exception.InvalidAcademicYearException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcademicYearTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 4, 1);
    private static final LocalDate END_DATE = LocalDate.of(2027, 3, 31);

    @Test
    void newYearBeginsAsPlanned() {
        assertThat(academicYear().getStatus()).isEqualTo(AcademicYearStatus.PLANNED);
    }

    @Test
    void nameIsNormalized() {
        AcademicYear academicYear = new AcademicYear(
                1L,
                "  2026-2027  ",
                START_DATE,
                END_DATE
        );

        assertThat(academicYear.getName()).isEqualTo("2026-2027");
    }

    @Test
    void invalidOrganizationIdIsRejected() {
        assertThatThrownBy(() -> new AcademicYear(
                0L,
                "2026-2027",
                START_DATE,
                END_DATE
        )).isInstanceOf(InvalidAcademicYearException.class)
                .hasMessage("Organization ID must be greater than zero");
    }

    @Test
    void blankNameIsRejected() {
        assertThatThrownBy(() -> new AcademicYear(
                1L,
                " ",
                START_DATE,
                END_DATE
        )).isInstanceOf(InvalidAcademicYearException.class)
                .hasMessage("Academic year name is required");
    }

    @Test
    void oversizedNameIsRejected() {
        assertThatThrownBy(() -> new AcademicYear(
                1L,
                "a".repeat(51),
                START_DATE,
                END_DATE
        )).isInstanceOf(InvalidAcademicYearException.class)
                .hasMessage("Academic year name cannot exceed 50 characters");
    }

    @Test
    void nullDatesAreRejected() {
        assertThatThrownBy(() -> new AcademicYear(
                1L,
                "2026-2027",
                null,
                END_DATE
        )).isInstanceOf(InvalidAcademicYearException.class)
                .hasMessage("Start date and end date are required");
    }

    @Test
    void invalidDatesAreRejected() {
        assertThatThrownBy(() -> new AcademicYear(
                1L,
                "2026-2027",
                END_DATE,
                START_DATE
        )).isInstanceOf(InvalidAcademicYearException.class)
                .hasMessage("Start date must be before end date");
    }

    @Test
    void plannedYearCanBeUpdated() {
        AcademicYear academicYear = academicYear();

        academicYear.updateDetails(
                "  2027-2028 ",
                LocalDate.of(2027, 4, 1),
                LocalDate.of(2028, 3, 31)
        );

        assertThat(academicYear.getName()).isEqualTo("2027-2028");
        assertThat(academicYear.getStartDate()).isEqualTo(LocalDate.of(2027, 4, 1));
        assertThat(academicYear.getEndDate()).isEqualTo(LocalDate.of(2028, 3, 31));
    }

    @Test
    void activeYearCannotBeUpdated() {
        AcademicYear academicYear = academicYearWithStatus(AcademicYearStatus.ACTIVE);

        assertThatThrownBy(() -> academicYear.updateDetails(
                "2027-2028",
                LocalDate.of(2027, 4, 1),
                LocalDate.of(2028, 3, 31)
        )).isInstanceOf(AcademicYearConflictException.class)
                .hasMessage("Only a planned academic year can be updated");
    }

    @Test
    void closedYearCannotBeUpdated() {
        AcademicYear academicYear = academicYearWithStatus(AcademicYearStatus.CLOSED);

        assertThatThrownBy(() -> academicYear.updateDetails(
                "2027-2028",
                LocalDate.of(2027, 4, 1),
                LocalDate.of(2028, 3, 31)
        )).isInstanceOf(AcademicYearConflictException.class)
                .hasMessage("Only a planned academic year can be updated");
    }

    @Test
    void onlyPlannedCanActivate() {
        AcademicYear academicYear = academicYear();

        academicYear.activate();

        assertThat(academicYear.getStatus()).isEqualTo(AcademicYearStatus.ACTIVE);
    }

    @Test
    void onlyActiveCanClose() {
        AcademicYear academicYear = academicYearWithStatus(AcademicYearStatus.ACTIVE);

        academicYear.close();

        assertThat(academicYear.getStatus()).isEqualTo(AcademicYearStatus.CLOSED);
    }

    @Test
    void invalidLifecycleTransitionsAreRejected() {
        assertThatThrownBy(() -> academicYearWithStatus(AcademicYearStatus.ACTIVE).activate())
                .isInstanceOf(AcademicYearConflictException.class)
                .hasMessage("Only a planned academic year can be activated");

        assertThatThrownBy(() -> academicYearWithStatus(AcademicYearStatus.CLOSED).activate())
                .isInstanceOf(AcademicYearConflictException.class)
                .hasMessage("Only a planned academic year can be activated");

        assertThatThrownBy(() -> academicYear().close())
                .isInstanceOf(AcademicYearConflictException.class)
                .hasMessage("Only an active academic year can be closed");

        assertThatThrownBy(() -> academicYearWithStatus(AcademicYearStatus.CLOSED).close())
                .isInstanceOf(AcademicYearConflictException.class)
                .hasMessage("Only an active academic year can be closed");
    }

    private static AcademicYear academicYear() {
        return new AcademicYear(
                1L,
                "2026-2027",
                START_DATE,
                END_DATE
        );
    }

    private static AcademicYear academicYearWithStatus(AcademicYearStatus status) {
        AcademicYear academicYear = academicYear();
        ReflectionTestUtils.setField(academicYear, "status", status);
        return academicYear;
    }
}
