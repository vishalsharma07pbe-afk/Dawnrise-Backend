package com.dawnrise.academic.studentattendance.importing.service;

import com.dawnrise.academic.studentattendance.importing.enums.StudentAttendanceImportFormat;
import com.dawnrise.academic.studentattendance.importing.exception.InvalidStudentAttendanceImportException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class StudentAttendanceImportFileParser {

    public static final List<String> HEADERS = List.of(
            "academic_year",
            "grade_code",
            "section_code",
            "attendance_date",
            "roll_number",
            "attendance_status",
            "remarks"
    );
    private static final int MAX_ROWS = 100;
    private final long maxFileBytes;

    public StudentAttendanceImportFileParser(
            @Value("${dawnrise.student-attendance.import.max-file-bytes:2097152}")
            long maxFileBytes
    ) {
        if (maxFileBytes < 1) {
            throw new IllegalArgumentException("Import file limit must be positive");
        }
        this.maxFileBytes = maxFileBytes;
    }

    public ParsedStudentAttendanceImport parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidStudentAttendanceImportException("Attendance import file is required");
        }
        if (file.getSize() > maxFileBytes) {
            throw new InvalidStudentAttendanceImportException(
                    "Attendance import file exceeds the configured size limit"
            );
        }
        String name = safeFileName(file.getOriginalFilename());
        StudentAttendanceImportFormat format = format(name);
        try (InputStream input = file.getInputStream()) {
            List<ParsedStudentAttendanceImportRow> rows = switch (format) {
                case CSV -> parseCsv(input);
                case XLSX -> parseXlsx(input);
            };
            if (rows.isEmpty()) {
                throw new InvalidStudentAttendanceImportException(
                        "Attendance import must contain at least one data row"
                );
            }
            if (rows.size() > MAX_ROWS) {
                throw new InvalidStudentAttendanceImportException(
                        "Attendance import cannot contain more than 100 data rows"
                );
            }
            return new ParsedStudentAttendanceImport(name, format, rows);
        } catch (InvalidStudentAttendanceImportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidStudentAttendanceImportException(
                    "Attendance import file could not be read",
                    exception
            );
        }
    }

    public byte[] csvTemplate() {
        return (String.join(",", HEADERS) + "\r\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private List<ParsedStudentAttendanceImportRow> parseCsv(InputStream input)
            throws IOException {
        Reader reader = new InputStreamReader(
                input,
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
        );
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();
        try (CSVParser parser = csvFormat.parse(reader)) {
            validateHeaders(new ArrayList<>(parser.getHeaderNames()));
            List<ParsedStudentAttendanceImportRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (rows.size() == MAX_ROWS) {
                    throw new InvalidStudentAttendanceImportException(
                            "Attendance import cannot contain more than 100 data rows"
                    );
                }
                rows.add(row(
                        Math.toIntExact(record.getRecordNumber() + 1),
                        header -> record.isMapped(header) ? record.get(header) : null,
                        List.of()
                ));
            }
            return rows;
        }
    }

    private List<ParsedStudentAttendanceImportRow> parseXlsx(InputStream input)
            throws IOException {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                return List.of();
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return List.of();
            }
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<String> headers = new ArrayList<>();
            if (headerRow.getLastCellNum() > HEADERS.size()) {
                throw new InvalidStudentAttendanceImportException(
                        "Attendance import headers must exactly match the template"
                );
            }
            for (int index = 0; index < HEADERS.size(); index++) {
                Cell headerCell = headerRow.getCell(index);
                if (headerCell != null && headerCell.getCellType() == CellType.FORMULA) {
                    throw new InvalidStudentAttendanceImportException(
                            "Spreadsheet formulas are not allowed"
                    );
                }
                headers.add(formatter.formatCellValue(headerCell).trim());
            }
            validateHeaders(headers);
            List<ParsedStudentAttendanceImportRow> rows = new ArrayList<>();
            for (int index = headerRow.getRowNum() + 1;
                 index <= sheet.getLastRowNum(); index++) {
                Row source = sheet.getRow(index);
                if (source == null || isBlank(source, formatter)) {
                    continue;
                }
                if (rows.size() == MAX_ROWS) {
                    throw new InvalidStudentAttendanceImportException(
                            "Attendance import cannot contain more than 100 data rows"
                    );
                }
                List<String> errors = new ArrayList<>();
                Map<String, String> values = new HashMap<>();
                for (int column = 0; column < HEADERS.size(); column++) {
                    Cell cell = source.getCell(column);
                    if (cell != null && cell.getCellType() == CellType.FORMULA) {
                        errors.add("Spreadsheet formulas are not allowed");
                        values.put(HEADERS.get(column), null);
                    } else {
                        values.put(
                                HEADERS.get(column),
                                cell == null ? null : formatter.formatCellValue(cell)
                        );
                    }
                }
                rows.add(row(index + 1, values::get, errors));
            }
            return rows;
        }
    }

    private boolean isBlank(Row row, DataFormatter formatter) {
        for (int column = 0; column < HEADERS.size(); column++) {
            Cell cell = row.getCell(column);
            if (cell != null && !formatter.formatCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private ParsedStudentAttendanceImportRow row(
            int rowNumber,
            java.util.function.Function<String, String> value,
            List<String> errors
    ) {
        return new ParsedStudentAttendanceImportRow(
                rowNumber,
                normalize(value.apply(HEADERS.get(0))),
                normalizeCode(value.apply(HEADERS.get(1))),
                normalizeCode(value.apply(HEADERS.get(2))),
                normalize(value.apply(HEADERS.get(3))),
                normalizeCode(value.apply(HEADERS.get(4))),
                normalizeCode(value.apply(HEADERS.get(5))),
                normalize(value.apply(HEADERS.get(6))),
                List.copyOf(errors)
        );
    }

    private void validateHeaders(List<String> actual) {
        List<String> normalized = actual.stream()
                .map(value -> value == null
                        ? ""
                        : value.replace("\uFEFF", "")
                        .trim()
                        .toLowerCase(Locale.ROOT))
                .toList();
        if (!normalized.equals(HEADERS)) {
            throw new InvalidStudentAttendanceImportException(
                    "Attendance import headers must exactly match the template"
            );
        }
    }

    private StudentAttendanceImportFormat format(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return StudentAttendanceImportFormat.CSV;
        }
        if (lower.endsWith(".xlsx")) {
            return StudentAttendanceImportFormat.XLSX;
        }
        throw new InvalidStudentAttendanceImportException(
                "Only CSV and XLSX attendance imports are supported"
        );
    }

    private String safeFileName(String supplied) {
        String value = supplied == null ? "attendance-import" : supplied;
        value = value.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        if (value.isEmpty()) {
            value = "attendance-import";
        }
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeCode(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
