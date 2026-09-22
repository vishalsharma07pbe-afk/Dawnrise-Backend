package com.dawnrise.academic.studentattendance.importing.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.studentattendance.importing.dto.*;
import com.dawnrise.academic.studentattendance.importing.entity.StudentAttendanceImportPreview;
import com.dawnrise.academic.studentattendance.importing.exception.InvalidStudentAttendanceImportException;
import com.dawnrise.academic.studentattendance.importing.exception.StudentAttendanceImportConflictException;
import com.dawnrise.academic.studentattendance.importing.exception.StudentAttendanceImportPreviewNotFoundException;
import com.dawnrise.academic.studentattendance.importing.repository.StudentAttendanceImportPreviewRepository;
import com.dawnrise.academic.studentattendance.importing.service.*;
import com.dawnrise.academic.studentattendance.offlinesync.dto.*;
import com.dawnrise.academic.studentattendance.offlinesync.enums.StudentAttendanceOfflineSyncMode;
import com.dawnrise.academic.studentattendance.offlinesync.service.StudentAttendanceOfflineSyncService;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.recording.exception.InvalidStudentAttendanceRecordingException;
import com.dawnrise.academic.studentattendance.recording.exception.StudentAttendanceRecordingConflictException;
import com.dawnrise.academic.studentattendance.recording.service.StudentAttendanceRecordingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudentAttendanceImportServiceImpl
        implements StudentAttendanceImportService {

    private final StudentAttendanceImportFileParser parser;
    private final StudentAttendanceImportFingerprintService fingerprintService;
    private final StudentAttendanceImportPreviewRepository previewRepository;
    private final AcademicYearRepository academicYearRepository;
    private final GradeLevelRepository gradeLevelRepository;
    private final SectionRepository sectionRepository;
    private final StudentAttendanceRecordingService recordingService;
    private final StudentAttendanceOfflineSyncService offlineSyncService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration previewTtl;
    private final TransactionTemplate transactionTemplate;

    public StudentAttendanceImportServiceImpl(
            StudentAttendanceImportFileParser parser,
            StudentAttendanceImportFingerprintService fingerprintService,
            StudentAttendanceImportPreviewRepository previewRepository,
            AcademicYearRepository academicYearRepository,
            GradeLevelRepository gradeLevelRepository,
            SectionRepository sectionRepository,
            StudentAttendanceRecordingService recordingService,
            StudentAttendanceOfflineSyncService offlineSyncService,
            ObjectMapper objectMapper,
            Clock clock,
            TransactionTemplate transactionTemplate,
            @Value("${dawnrise.student-attendance.import.preview-ttl:PT30M}")
            Duration previewTtl
    ) {
        if (previewTtl.isZero() || previewTtl.isNegative()) {
            throw new IllegalArgumentException("Attendance import preview TTL must be positive");
        }
        this.parser = parser;
        this.fingerprintService = fingerprintService;
        this.previewRepository = previewRepository;
        this.academicYearRepository = academicYearRepository;
        this.gradeLevelRepository = gradeLevelRepository;
        this.sectionRepository = sectionRepository;
        this.recordingService = recordingService;
        this.offlineSyncService = offlineSyncService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
        this.previewTtl = previewTtl;
    }

    @Override
    public byte[] csvTemplate() {
        return parser.csvTemplate();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StudentAttendanceImportPreviewResponse preview(
            long organizationId,
            long actorUserId,
            MultipartFile file
    ) {
        ParsedStudentAttendanceImport parsed = parser.parse(file);
        List<MutableRow> rows = parsed.rows().stream()
                .map(MutableRow::new)
                .toList();
        List<String> previewErrors = new ArrayList<>();
        ImportContext context = resolveContext(organizationId, rows, previewErrors);
        StudentAttendanceOfflineDraftSnapshot snapshot = null;
        if (context != null && previewErrors.isEmpty()) {
            try {
                snapshot = recordingService.previewOfflineDraft(
                        organizationId,
                        actorUserId,
                        context.academicYear().getId(),
                        context.gradeLevel().getId(),
                        context.section().getId(),
                        context.attendanceDate()
                );
            } catch (AccessDeniedException exception) {
                throw exception;
            } catch (InvalidStudentAttendanceRecordingException
                     | StudentAttendanceRecordingConflictException exception) {
                previewErrors.add(exception.getMessage());
            }
        }
        validateRows(rows, snapshot, previewErrors);
        List<StudentAttendanceImportRowResponse> normalizedRows = rows.stream()
                .map(MutableRow::response)
                .toList();
        boolean canConfirm = context != null
                && snapshot != null
                && previewErrors.isEmpty()
                && normalizedRows.stream().allMatch(row -> row.errors().isEmpty());
        String fingerprint = fingerprintService.fingerprint(
                organizationId,
                actorUserId,
                parsed.originalFileName(),
                normalizedRows
        );
        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plus(previewTtl);
        StudentAttendanceImportPreviewResponse storedResponse = response(
                null,
                fingerprint,
                parsed,
                canConfirm,
                context,
                snapshot,
                expiresAt,
                previewErrors,
                normalizedRows
        );
        String payloadJson = writePayload(new StoredStudentAttendanceImportPayload(
                storedResponse,
                snapshot == null ? List.of() : snapshot.roster().stream()
                        .map(StudentAttendanceOfflineDraftRosterEntry::studentEnrollmentId)
                        .toList()
        ));
        StudentAttendanceImportPreview entity = transactionTemplate.execute(status ->
                previewRepository.saveAndFlush(new StudentAttendanceImportPreview(
                                organizationId,
                                actorUserId,
                                context == null ? null : context.academicYear().getId(),
                                context == null ? null : context.gradeLevel().getId(),
                                context == null ? null : context.section().getId(),
                                context == null ? null : context.attendanceDate(),
                                fingerprint,
                                parsed.originalFileName(),
                                parsed.format(),
                                normalizedRows.size(),
                                canConfirm,
                                payloadJson,
                                expiresAt
                        )
                )
        );
        return response(
                entity.getId(), fingerprint, parsed, canConfirm, context,
                snapshot, expiresAt, previewErrors, normalizedRows
        );
    }

    @Override
    public StudentAttendanceOfflineSyncResponse confirm(
            long organizationId,
            long actorUserId,
            long previewId,
            String idempotencyKey,
            ConfirmStudentAttendanceImportRequest request
    ) {
        if (request == null || request.previewFingerprint() == null) {
            throw new InvalidStudentAttendanceImportException(
                    "Preview fingerprint is required"
            );
        }
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        StoredStudentAttendanceImportPayload stored = transactionTemplate.execute(status -> {
            StudentAttendanceImportPreview entity = previewForUpdate(
                    previewId, organizationId, actorUserId);
            validateConfirmable(entity, request.previewFingerprint(), normalizedKey);
            StoredStudentAttendanceImportPayload payload = readPayload(entity.getPayloadJson());
            if (!payload.preview().canConfirm()) {
                throw new StudentAttendanceImportConflictException(
                        "Attendance import preview contains errors"
                );
            }
            entity.reserveConfirmation(normalizedKey);
            previewRepository.saveAndFlush(entity);
            return payload;
        });
        StudentAttendanceImportPreviewResponse preview = stored.preview();
        StudentAttendanceOfflineSyncRequest syncRequest =
                new StudentAttendanceOfflineSyncRequest(
                        preview.academicYearId(),
                        preview.gradeLevelId(),
                        preview.sectionId(),
                        preview.attendanceDate(),
                        preview.baseSessionVersion(),
                        StudentAttendanceOfflineSyncMode.COMPLETE,
                        stored.expectedRosterEnrollmentIds(),
                        preview.rows().stream()
                                .map(row -> new StudentAttendanceOfflineSyncRecordRequest(
                                        row.studentEnrollmentId(),
                                        row.expectedRecordVersion(),
                                        row.attendanceStatus(),
                                        row.remarks()
                                ))
                                .toList()
                );
        StudentAttendanceOfflineSyncResponse result = offlineSyncService.synchronize(
                organizationId,
                actorUserId,
                normalizedKey,
                syncRequest
        );
        transactionTemplate.executeWithoutResult(status -> {
            StudentAttendanceImportPreview entity = previewForUpdate(
                    previewId, organizationId, actorUserId);
            entity.markConfirmed(result.operationId(), normalizedKey);
            previewRepository.saveAndFlush(entity);
        });
        return result;
    }

    private StudentAttendanceImportPreview previewForUpdate(
            long previewId,
            long organizationId,
            long actorUserId
    ) {
        return previewRepository.findForUpdate(previewId, organizationId, actorUserId)
                .orElseThrow(() -> new StudentAttendanceImportPreviewNotFoundException(
                        "Attendance import preview was not found"
                ));
    }

    private void validateConfirmable(
            StudentAttendanceImportPreview entity,
            String fingerprint,
            String idempotencyKey
    ) {
        if (!Objects.equals(entity.getPreviewFingerprint(), fingerprint)) {
            throw new StudentAttendanceImportConflictException(
                    "Attendance import preview fingerprint does not match"
            );
        }
        if (entity.getConfirmedIdempotencyKey() == null
                && entity.getExpiresAt().isBefore(OffsetDateTime.now(clock))) {
            throw new StudentAttendanceImportConflictException(
                    "Attendance import preview has expired"
            );
        }
        if (!Boolean.TRUE.equals(entity.getCanConfirm())) {
            throw new StudentAttendanceImportConflictException(
                    "Attendance import preview contains errors"
            );
        }
        if (entity.getConfirmedIdempotencyKey() != null
                && !entity.getConfirmedIdempotencyKey().equals(idempotencyKey)) {
            throw new StudentAttendanceImportConflictException(
                    "Attendance import preview was already confirmed with another idempotency key"
            );
        }
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidStudentAttendanceImportException(
                    "Idempotency-Key header is required"
            );
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw new InvalidStudentAttendanceImportException(
                    "Idempotency-Key header cannot exceed 128 characters"
            );
        }
        return normalized;
    }

    private ImportContext resolveContext(
            long organizationId,
            List<MutableRow> rows,
            List<String> previewErrors
    ) {
        for (MutableRow row : rows) {
            row.validateBasic();
        }
        Set<String> contexts = rows.stream()
                .map(MutableRow::contextKey)
                .collect(Collectors.toSet());
        if (contexts.size() != 1 || rows.stream().anyMatch(MutableRow::hasContextErrors)) {
            if (contexts.size() != 1) {
                previewErrors.add("All import rows must use the same academic year, grade, section and date");
            }
            return null;
        }
        MutableRow first = rows.getFirst();
        AcademicYear year = academicYearRepository
                .findByOrganizationIdAndNameIgnoreCaseAndStatus(
                        organizationId,
                        first.academicYear,
                        AcademicYearStatus.ACTIVE
                )
                .orElse(null);
        if (year == null) {
            rows.forEach(row -> row.errors.add("Active academic year was not found"));
            return null;
        }
        GradeLevel grade = gradeLevelRepository
                .findByOrganizationIdAndAcademicYearIdAndCodeIgnoreCase(
                        organizationId, year.getId(), first.gradeCode)
                .orElse(null);
        if (grade == null) {
            rows.forEach(row -> row.errors.add("Grade code was not found in the academic year"));
            return null;
        }
        Section section = sectionRepository
                .findByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndCodeIgnoreCase(
                        organizationId, year.getId(), grade.getId(), first.sectionCode)
                .orElse(null);
        if (section == null) {
            rows.forEach(row -> row.errors.add("Section code was not found in the grade"));
            return null;
        }
        return new ImportContext(year, grade, section, first.date);
    }

    private void validateRows(
            List<MutableRow> rows,
            StudentAttendanceOfflineDraftSnapshot snapshot,
            List<String> previewErrors
    ) {
        if (snapshot == null) {
            return;
        }
        Map<String, StudentAttendanceOfflineDraftRosterEntry> roster = new HashMap<>();
        for (StudentAttendanceOfflineDraftRosterEntry entry : snapshot.roster()) {
            String key = entry.rollNumber().trim().toUpperCase(Locale.ROOT);
            if (roster.putIfAbsent(key, entry) != null) {
                previewErrors.add(
                        "Section roster contains duplicate roll numbers and cannot be imported"
                );
            }
        }
        Set<String> seen = new HashSet<>();
        for (MutableRow row : rows) {
            if (!seen.add(row.rollNumber)) {
                row.errors.add("Duplicate roll number in import");
            }
            StudentAttendanceOfflineDraftRosterEntry enrollment = roster.get(row.rollNumber);
            if (enrollment == null) {
                row.errors.add("Student roll number was not found in the section roster for this date");
            } else {
                row.enrollmentId = enrollment.studentEnrollmentId();
                row.expectedRecordVersion = enrollment.existingRecordVersion();
            }
        }
        Set<Long> imported = rows.stream()
                .map(row -> row.enrollmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> required = snapshot.roster().stream()
                .map(StudentAttendanceOfflineDraftRosterEntry::studentEnrollmentId)
                .collect(Collectors.toSet());
        if (!imported.equals(required)) {
            previewErrors.add("Complete attendance import must contain every student in the section roster exactly once");
        }
    }

    private StudentAttendanceImportPreviewResponse response(
            Long id,
            String fingerprint,
            ParsedStudentAttendanceImport parsed,
            boolean canConfirm,
            ImportContext context,
            StudentAttendanceOfflineDraftSnapshot snapshot,
            OffsetDateTime expiresAt,
            List<String> errors,
            List<StudentAttendanceImportRowResponse> rows
    ) {
        return new StudentAttendanceImportPreviewResponse(
                id,
                fingerprint,
                parsed.originalFileName(),
                parsed.format(),
                canConfirm,
                context == null ? null : context.academicYear().getId(),
                context == null ? null : context.gradeLevel().getId(),
                context == null ? null : context.section().getId(),
                context == null ? null : context.attendanceDate(),
                snapshot == null ? null : snapshot.baseSessionVersion(),
                rows.size(),
                expiresAt,
                List.copyOf(errors),
                rows
        );
    }

    private String writePayload(StoredStudentAttendanceImportPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new InvalidStudentAttendanceImportException(
                    "Attendance import preview could not be stored",
                    exception
            );
        }
    }

    private StoredStudentAttendanceImportPayload readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, StoredStudentAttendanceImportPayload.class);
        } catch (Exception exception) {
            throw new StudentAttendanceImportConflictException(
                    "Stored attendance import preview could not be read"
            );
        }
    }

    private record ImportContext(
            AcademicYear academicYear,
            GradeLevel gradeLevel,
            Section section,
            LocalDate attendanceDate
    ) {
    }

    private static final class MutableRow {
        private final int rowNumber;
        private final String academicYear;
        private final String gradeCode;
        private final String sectionCode;
        private final String rawDate;
        private final String rollNumber;
        private final String rawStatus;
        private final String remarks;
        private final List<String> errors;
        private LocalDate date;
        private AttendanceStatus status;
        private Long enrollmentId;
        private Long expectedRecordVersion;

        private MutableRow(ParsedStudentAttendanceImportRow row) {
            this.rowNumber = row.rowNumber();
            this.academicYear = row.academicYear();
            this.gradeCode = row.gradeCode();
            this.sectionCode = row.sectionCode();
            this.rawDate = row.attendanceDate();
            this.rollNumber = row.rollNumber();
            this.rawStatus = row.attendanceStatus();
            this.remarks = row.remarks();
            this.errors = new ArrayList<>(row.parsingErrors());
        }

        private void validateBasic() {
            require(academicYear, "Academic year is required");
            require(gradeCode, "Grade code is required");
            require(sectionCode, "Section code is required");
            require(rollNumber, "Roll number is required");
            if (rawDate == null) {
                errors.add("Attendance date is required");
            } else {
                try {
                    date = LocalDate.parse(rawDate);
                } catch (DateTimeParseException exception) {
                    errors.add("Attendance date must use ISO format YYYY-MM-DD");
                }
            }
            if (rawStatus == null) {
                errors.add("Attendance status is required");
            } else {
                try {
                    status = AttendanceStatus.valueOf(rawStatus);
                    if (!AttendanceStatus.finalStatuses().contains(status)) {
                        errors.add("Attendance status is not supported for recording");
                    }
                } catch (IllegalArgumentException exception) {
                    errors.add("Attendance status is invalid");
                }
            }
            if (remarks != null && remarks.length() > 500) {
                errors.add("Remarks cannot exceed 500 characters");
            }
        }

        private void require(String value, String message) {
            if (value == null) {
                errors.add(message);
            }
        }

        private String contextKey() {
            return String.join("|",
                    Objects.toString(academicYear, ""),
                    Objects.toString(gradeCode, ""),
                    Objects.toString(sectionCode, ""),
                    Objects.toString(date, ""));
        }

        private boolean hasContextErrors() {
            return academicYear == null
                    || gradeCode == null
                    || sectionCode == null
                    || date == null;
        }

        private StudentAttendanceImportRowResponse response() {
            return new StudentAttendanceImportRowResponse(
                    rowNumber,
                    academicYear,
                    gradeCode,
                    sectionCode,
                    date,
                    rollNumber,
                    enrollmentId,
                    expectedRecordVersion,
                    status,
                    remarks,
                    List.copyOf(errors)
            );
        }
    }
}
