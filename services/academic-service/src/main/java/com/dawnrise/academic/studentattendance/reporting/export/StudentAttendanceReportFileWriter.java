package com.dawnrise.academic.studentattendance.reporting.export;

import com.dawnrise.academic.studentattendance.reporting.exception.InvalidStudentAttendanceReportException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.temporal.TemporalAccessor;
import java.util.List;

@Component
public class StudentAttendanceReportFileWriter {

    public byte[] write(
            StudentAttendanceReportExportFormat format,
            List<String> headers,
            List<? extends List<?>> rows
    ) {
        if (format == null) {
            throw new InvalidStudentAttendanceReportException(
                    "Export format is required"
            );
        }

        validateTable(headers, rows);

        return switch (format) {
            case CSV -> writeCsv(headers, rows);
            case XLSX -> writeXlsx(headers, rows);
        };
    }

    private byte[] writeCsv(
            List<String> headers,
            List<? extends List<?>> rows
    ) {
        StringBuilder output = new StringBuilder();

        appendCsvRow(output, headers);

        for (List<?> row : rows) {
            appendCsvRow(output, row);
        }

        return output.toString()
                .getBytes(StandardCharsets.UTF_8);
    }

    private void appendCsvRow(
            StringBuilder output,
            List<?> values
    ) {
        for (int index = 0;
             index < values.size();
             index++) {

            if (index > 0) {
                output.append(',');
            }

            String value = exportText(values.get(index));

            output.append('"')
                    .append(
                            value.replace(
                                    "\"",
                                    "\"\""
                            )
                    )
                    .append('"');
        }

        output.append("\r\n");
    }

    private byte[] writeXlsx(
            List<String> headers,
            List<? extends List<?>> rows
    ) {
        try (
                Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {
            Sheet sheet = workbook.createSheet(
                    "Attendance Report"
            );

            CellStyle headerStyle =
                    createHeaderStyle(workbook);

            Row headerRow = sheet.createRow(0);

            for (int index = 0;
                 index < headers.size();
                 index++) {

                Cell cell = headerRow.createCell(index);
                cell.setCellValue(headers.get(index));
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;

            for (List<?> values : rows) {
                Row row = sheet.createRow(rowIndex++);

                for (int columnIndex = 0;
                     columnIndex < values.size();
                     columnIndex++) {

                    Cell cell =
                            row.createCell(columnIndex);

                    writeXlsxCell(
                            cell,
                            values.get(columnIndex)
                    );
                }
            }

            sheet.createFreezePane(0, 1);

            for (int index = 0;
                 index < headers.size();
                 index++) {

                sheet.autoSizeColumn(index);

                int width = Math.min(
                        sheet.getColumnWidth(index) + 512,
                        15000
                );

                sheet.setColumnWidth(index, width);
            }

            workbook.write(output);

            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Attendance report could not be generated",
                    exception
            );
        }
    }

    private CellStyle createHeaderStyle(
            Workbook workbook
    ) {
        Font font = workbook.createFont();
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);

        style.setFillForegroundColor(
                IndexedColors.LIGHT_CORNFLOWER_BLUE
                        .getIndex()
        );

        style.setFillPattern(
                FillPatternType.SOLID_FOREGROUND
        );

        return style;
    }

    private void writeXlsxCell(
            Cell cell,
            Object value
    ) {
        if (value == null) {
            cell.setBlank();
            return;
        }

        if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
            return;
        }

        if (value instanceof Long
                || value instanceof BigInteger) {
            cell.setCellValue(exportText(value));
            return;
        }

        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }

        if (value instanceof Boolean booleanValue) {
            cell.setCellValue(booleanValue);
            return;
        }

        cell.setCellValue(exportText(value));
    }

    private String exportText(Object value) {
        if (value == null) {
            return "";
        }

        String text;

        if (value instanceof Enum<?> enumValue) {
            text = enumValue.name();
        } else if (value instanceof TemporalAccessor) {
            text = value.toString();
        } else {
            text = String.valueOf(value);
        }

        return preventSpreadsheetFormula(text);
    }

    private String preventSpreadsheetFormula(
            String value
    ) {
        if (value.isEmpty()) {
            return value;
        }

        String inspectedValue = value.stripLeading();

        if (inspectedValue.isEmpty()) {
            return value;
        }

        char firstCharacter =
                inspectedValue.charAt(0);

        if (firstCharacter == '='
                || firstCharacter == '+'
                || firstCharacter == '-'
                || firstCharacter == '@'
                || firstCharacter == '\t'
                || firstCharacter == '\r'
                || firstCharacter == '\n') {
            return "'" + value;
        }

        return value;
    }

    private void validateTable(
            List<String> headers,
            List<? extends List<?>> rows
    ) {
        if (headers == null || headers.isEmpty()) {
            throw new InvalidStudentAttendanceReportException(
                    "Export headers are required"
            );
        }

        if (rows == null) {
            throw new InvalidStudentAttendanceReportException(
                    "Export rows are required"
            );
        }

        for (String header : headers) {
            if (header == null || header.isBlank()) {
                throw new InvalidStudentAttendanceReportException(
                        "Export headers cannot be blank"
                );
            }
        }

        for (List<?> row : rows) {
            if (row == null
                    || row.size() != headers.size()) {
                throw new InvalidStudentAttendanceReportException(
                        "Export row does not match headers"
                );
            }
        }
    }
}
