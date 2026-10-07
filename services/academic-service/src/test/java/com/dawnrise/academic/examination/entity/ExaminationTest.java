package com.dawnrise.academic.examination.entity;

import com.dawnrise.academic.examination.enums.ExaminationStatus;
import com.dawnrise.academic.examination.exception.ExaminationConflictException;
import com.dawnrise.academic.examination.exception.InvalidExaminationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExaminationTest {
    @Test
    void publicationFreezesTheExaminationAndKeepsItsPublicationTimeOnCancel() {
        Examination examination = new Examination(1, 2, " Mid-term ");
        assertEquals("Mid-term", examination.getName());

        examination.publish();
        assertEquals(ExaminationStatus.PUBLISHED, examination.getStatus());
        assertNotNull(examination.getPublishedAt());
        assertThrows(ExaminationConflictException.class,
                () -> examination.rename("Changed"));

        examination.cancel();
        assertEquals(ExaminationStatus.CANCELLED, examination.getStatus());
        assertNotNull(examination.getPublishedAt());
    }

    @Test
    void assessmentRejectsInvalidTimeAndMarks() {
        assertThrows(InvalidExaminationException.class, () -> new ScheduledAssessment(
                1, 2, 3, 4, 5, LocalDate.of(2026, 10, 5),
                LocalTime.of(11, 0), LocalTime.of(10, 0), BigDecimal.TEN));
        assertThrows(InvalidExaminationException.class, () -> new ScheduledAssessment(
                1, 2, 3, 4, 5, LocalDate.of(2026, 10, 5),
                LocalTime.of(9, 0), LocalTime.of(10, 0), BigDecimal.ZERO));
    }
}
