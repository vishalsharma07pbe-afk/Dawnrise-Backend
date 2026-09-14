package com.dawnrise.academic.studentattendance.reporting.export;

import com.dawnrise.academic.studentattendance.reporting.exception.InvalidStudentAttendanceReportException;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAttendanceReportFileWriterTest {

    private final StudentAttendanceReportFileWriter writer =
            new StudentAttendanceReportFileWriter();

    @Test
    void writesUtf8CsvWithEscapingAndFormulaProtection() {
        byte[] bytes = writer.write(
                StudentAttendanceReportExportFormat.CSV,
                List.of("ID", "Name", "Remarks", "Blank"),
                List.of(Arrays.asList(
                        1L,
                        "A, \"quoted\"",
                        "\t =SUM(A1:A2)\r\nnext",
                        null
                ))
        );

        String csv = new String(bytes, StandardCharsets.UTF_8);

        assertThat(bytes[0]).isNotEqualTo((byte) 0xEF);
        assertThat(csv).isEqualTo(
                "\"ID\",\"Name\",\"Remarks\",\"Blank\"\r\n"
                        + "\"1\",\"A, \"\"quoted\"\"\","
                        + "\"'\t =SUM(A1:A2)\r\nnext\",\"\"\r\n"
        );
    }

    @Test
    void rejectsMismatchedRowWidth() {
        assertThatThrownBy(() -> writer.write(
                StudentAttendanceReportExportFormat.CSV,
                List.of("A", "B"),
                List.of(List.of("A"))
        )).isInstanceOf(InvalidStudentAttendanceReportException.class)
                .hasMessage("Export row does not match headers");
    }

    @Test
    void writesOpenableXlsxWithHeaderStyleFreezePaneAndSafeCells()
            throws Exception {
        long largeId = 9_007_199_254_740_993L;
        byte[] bytes = writer.write(
                StudentAttendanceReportExportFormat.XLSX,
                List.of("Student Enrollment ID", "Roll", "Credit", "Blank"),
                List.of(Arrays.asList(
                        largeId,
                        "=HYPERLINK(\"x\")",
                        new BigDecimal("0.75"),
                        null
                ))
        );

        try (Workbook workbook =
                     new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("Attendance Report");

            assertThat(sheet).isNotNull();
            assertThat(sheet.getPaneInformation()).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("Student Enrollment ID");
            assertThat(sheet.getRow(0).getCell(0).getCellStyle()
                    .getFillPattern()).isNotNull();
            assertThat(workbook.getFontAt(
                    sheet.getRow(0).getCell(0).getCellStyle()
                            .getFontIndex()
            ).getBold()).isTrue();
            assertThat(sheet.getRow(1).getCell(0).getCellType())
                    .isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("9007199254740993");
            assertThat(sheet.getRow(1).getCell(1).getCellType())
                    .isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("'=HYPERLINK(\"x\")");
            assertThat(sheet.getRow(1).getCell(1).getCellType())
                    .isNotEqualTo(CellType.FORMULA);
            assertThat(sheet.getRow(1).getCell(2).getCellType())
                    .isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue())
                    .isEqualTo(0.75d);
            assertThat(sheet.getRow(1).getCell(3).getCellType())
                    .isEqualTo(CellType.BLANK);
        }
    }
}
