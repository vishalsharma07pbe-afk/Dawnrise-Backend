package com.dawnrise.academic.reportcard.service;

import com.dawnrise.academic.reportcard.dto.*;
import com.dawnrise.academic.reportcard.enums.ReportCardStatus;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

import java.math.*;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportCardPdfServiceTest {
    @Test
    void createsStructuredAccessiblePdfAndSafeFilename() throws Exception {
        var now = OffsetDateTime.now();
        var card = new ReportCardResponse(1, 2, 3, 4, 5, 6, 7, "Student Name", 8, ReportCardStatus.PUBLISHED, "2026", "Final", "Grade 1", "A", "R/1", now, "Policy", 1, new BigDecimal("40"), new BigDecimal("40"), true, true, 2, RoundingMode.HALF_UP, List.of(new ReportCardSubjectResponse(9, "M", "Math", new BigDecimal("100"), new BigDecimal("80"), false, new BigDecimal("80"), "A", true, 1)), new BigDecimal("100"), new BigDecimal("80"), new BigDecimal("80"), "A", true, 0, false, null, 0, now);
        var service = new ReportCardPdfService();
        byte[] bytes = service.create(card);
        assertTrue(bytes.length > 100);
        assertEquals("%PDF", new String(bytes, 0, 4));
        try (var document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("REPORT CARD"));
            assertTrue(text.contains("STUDENT INFORMATION"));
            assertTrue(text.contains("Student Name"));
            assertTrue(text.contains("RESULT SUMMARY"));
            assertTrue(text.contains("SUBJECT PERFORMANCE"));
            assertTrue(text.contains("OBTAINED"));
            assertTrue(text.contains("Math"));
            assertTrue(text.contains("80%"));
            assertTrue(text.contains("PASS"));
        }
        assertEquals("report-card-1-R-1.pdf", service.filename(card));
    }
}
