package com.dawnrise.academic.studentattendance.importing.service;

import com.dawnrise.academic.studentattendance.importing.exception.InvalidStudentAttendanceImportException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAttendanceImportFileParserTest {

    private final StudentAttendanceImportFileParser parser =
            new StudentAttendanceImportFileParser(2_097_152);

    @Test
    void parsesQuotedCsvAndNormalizesCodes() {
        String csv = String.join(",", StudentAttendanceImportFileParser.HEADERS)
                + "\n2026-2027,class_1,a,2026-09-11,dr-001,present,\"Arrived, on time\"\n";

        ParsedStudentAttendanceImport parsed = parser.parse(new MockMultipartFile(
                "file", "attendance.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        ));

        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().getFirst().gradeCode()).isEqualTo("CLASS_1");
        assertThat(parsed.rows().getFirst().remarks()).isEqualTo("Arrived, on time");
    }

    @Test
    void rejectsWrongHeadersAndOversizedFiles() {
        assertThatThrownBy(() -> parser.parse(new MockMultipartFile(
                "file", "attendance.csv", "text/csv",
                "wrong,headers\n1,2\n".getBytes(StandardCharsets.UTF_8)
        ))).isInstanceOf(InvalidStudentAttendanceImportException.class)
                .hasMessageContaining("headers");

        StudentAttendanceImportFileParser tiny = new StudentAttendanceImportFileParser(4);
        assertThatThrownBy(() -> tiny.parse(new MockMultipartFile(
                "file", "attendance.csv", "text/csv", new byte[5]
        ))).isInstanceOf(InvalidStudentAttendanceImportException.class)
                .hasMessageContaining("size limit");
    }

    @Test
    void xlsxFormulaIsReportedWithoutEvaluation() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Attendance");
            var header = sheet.createRow(0);
            for (int index = 0; index < StudentAttendanceImportFileParser.HEADERS.size(); index++) {
                header.createCell(index).setCellValue(
                        StudentAttendanceImportFileParser.HEADERS.get(index)
                );
            }
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("2026-2027");
            row.createCell(1).setCellValue("CLASS_1");
            row.createCell(2).setCellValue("A");
            row.createCell(3).setCellValue("2026-09-11");
            row.createCell(4).setCellValue("DR-001");
            row.createCell(5).setCellFormula("1+1");
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        ParsedStudentAttendanceImport parsed = parser.parse(new MockMultipartFile(
                "file", "attendance.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookBytes
        ));

        assertThat(parsed.rows().getFirst().parsingErrors())
                .contains("Spreadsheet formulas are not allowed");
        assertThat(parsed.rows().getFirst().attendanceStatus()).isNull();
    }

    @Test
    void templateContainsOnlySafeFixedHeaders() {
        assertThat(new String(parser.csvTemplate(), StandardCharsets.UTF_8))
                .isEqualTo(String.join(",", StudentAttendanceImportFileParser.HEADERS) + "\r\n")
                .doesNotContain("=", "+", "-", "@");
    }
}
