package com.dawnrise.academic.reportcard.service;

import com.dawnrise.academic.reportcard.dto.*;
import com.dawnrise.academic.reportcard.enums.ReportCardStatus;
import org.junit.jupiter.api.Test;

import java.math.*;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportCardPdfServiceTest {
    @Test
    void createsAccessiblePdfAndSafeFilename() {
        var now = OffsetDateTime.now();
        var card = new ReportCardResponse(1, 2, 3, 4, 5, 6, 7, 8, ReportCardStatus.PUBLISHED, "2026", "Final", "Grade 1", "A", "R/1", now, "Policy", 1, new BigDecimal("40"), new BigDecimal("40"), true, true, 2, RoundingMode.HALF_UP, List.of(new ReportCardSubjectResponse(9, "M", "Math", new BigDecimal("100"), new BigDecimal("80"), false, new BigDecimal("80"), "A", true, 1)), new BigDecimal("100"), new BigDecimal("80"), new BigDecimal("80"), "A", true, 0, false, null, 0, now);
        var service = new ReportCardPdfService();
        byte[] bytes = service.create(card);
        assertTrue(bytes.length > 100);
        assertEquals("%PDF", new String(bytes, 0, 4));
        assertEquals("report-card-1-R-1.pdf", service.filename(card));
    }
}
