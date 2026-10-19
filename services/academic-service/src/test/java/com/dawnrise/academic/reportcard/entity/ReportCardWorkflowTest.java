package com.dawnrise.academic.reportcard.entity;

import com.dawnrise.academic.reportcard.enums.ReportCardStatus;
import com.dawnrise.academic.results.exception.*;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.math.*;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportCardWorkflowTest {
    private ReportCard card() throws Exception {
        var c = new ReportCard(1, 2, 3, 4, 5, 6, 7, 8, 9, "2026", "Exam", "Grade", "A", "R1", OffsetDateTime.now(), "Policy", 1, new BigDecimal("40"), new BigDecimal("40"), true, true, 2, RoundingMode.HALF_UP, List.of(new ReportCardSubject(1, 10, "M", "Math", new BigDecimal("100"), new BigDecimal("80"), false, new BigDecimal("80"), "A", true, 1)), new BigDecimal("100"), new BigDecimal("80"), new BigDecimal("80"), "A", true, 0, false, 11);
        Field f = ReportCard.class.getDeclaredField("version");
        f.setAccessible(true);
        f.set(c, 0L);
        return c;
    }

    @Test
    void enforcesLegalWorkflowAndVersion() throws Exception {
        var c = card();
        c.submit(0);
        assertEquals(ReportCardStatus.IN_REVIEW, c.getStatus());
        c.approve(0, 12);
        assertEquals(ReportCardStatus.APPROVED, c.getStatus());
        c.publish(0, 12);
        assertEquals(ReportCardStatus.PUBLISHED, c.getStatus());
        assertNotNull(c.getPublishedAt());
        assertThrows(ResultConflictException.class, () -> c.submit(0));
    }

    @Test
    void requiresReturnReason() throws Exception {
        var c = card();
        c.submit(0);
        assertThrows(InvalidResultException.class, () -> c.returnToDraft(0, 12, " "));
        c.returnToDraft(0, 12, "Correction needed");
        assertEquals(ReportCardStatus.DRAFT, c.getStatus());
        assertEquals("Correction needed", c.getReturnReason());
    }

    @Test
    void rejectsStaleVersion() throws Exception {
        assertThrows(ResultConflictException.class, () -> card().submit(99));
    }
}
