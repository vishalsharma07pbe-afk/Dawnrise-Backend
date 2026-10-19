package com.dawnrise.academic.reportcard.service;

import com.dawnrise.academic.reportcard.dto.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Locale;

@Service
public class ReportCardPdfService {
    public byte[] create(ReportCardResponse card) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream s = new PDPageContentStream(doc, page)) {
                PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                float y = 800;
                y = line(s, bold, 16, 50, y, "DAWNRISE REPORT CARD");
                y = line(s, normal, 10, 50, y, "Report card ID: " + card.id());
                y = line(s, normal, 10, 50, y, "Academic year: " + card.academicYearName() + "   Examination: " + card.examinationName());
                y = line(s, normal, 10, 50, y, "Student user ID: " + card.studentUserId() + "   Roll number: " + card.rollNumber());
                y = line(s, normal, 10, 50, y, "Grade: " + card.gradeName() + "   Section: " + card.sectionName());
                y -= 8;
                y = line(s, bold, 10, 50, y, "Subject | Maximum | Obtained | Percentage | Grade | Outcome");
                for (var row : card.subjects()) {
                    String obtained = row.absent() ? "Absent" : row.obtainedMarks().toPlainString();
                    String pct = row.percentage() == null ? "-" : row.percentage().toPlainString() + "%";
                    y = line(s, normal, 9, 50, y, row.subjectCode() + " " + row.subjectName() + " | " + row.maximumMarks() + " | " + obtained + " | " + pct + " | " + (row.gradeLabel() == null ? "-" : row.gradeLabel()) + " | " + (row.passed() ? "Pass" : "Fail"));
                }
                y -= 8;
                y = line(s, bold, 10, 50, y, "Totals: " + card.totalObtainedMarks() + " / " + card.totalMaximumMarks());
                y = line(s, bold, 10, 50, y, "Aggregate: " + card.aggregatePercentage() + "%   Grade: " + card.overallGrade() + "   Result: " + (card.passed() ? "PASS" : "FAIL"));
                line(s, normal, 9, 50, y, "Published: " + card.publishedAt() + (card.partial() ? "   Partial published result" : ""));
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate report-card PDF", e);
        }
    }

    private static float line(PDPageContentStream s, PDFont font, float size, float x, float y, String text) throws IOException {
        s.beginText();
        s.setFont(font, size);
        s.newLineAtOffset(x, y);
        s.showText(text.replaceAll("[^\\x20-\\x7E]", "-"));
        s.endText();
        return y - 18;
    }

    public String filename(ReportCardResponse c) {
        return String.format(Locale.ROOT, "report-card-%d-%s.pdf", c.id(), c.rollNumber().replaceAll("[^A-Za-z0-9_-]", "-"));
    }
}
