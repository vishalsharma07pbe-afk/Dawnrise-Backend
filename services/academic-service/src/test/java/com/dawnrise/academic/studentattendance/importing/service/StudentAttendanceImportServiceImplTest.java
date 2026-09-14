package com.dawnrise.academic.studentattendance.importing.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.studentattendance.importing.dto.*;
import com.dawnrise.academic.studentattendance.importing.entity.StudentAttendanceImportPreview;
import com.dawnrise.academic.studentattendance.importing.enums.StudentAttendanceImportFormat;
import com.dawnrise.academic.studentattendance.importing.exception.StudentAttendanceImportConflictException;
import com.dawnrise.academic.studentattendance.importing.repository.StudentAttendanceImportPreviewRepository;
import com.dawnrise.academic.studentattendance.importing.service.impl.StudentAttendanceImportServiceImpl;
import com.dawnrise.academic.studentattendance.offlinesync.dto.*;
import com.dawnrise.academic.studentattendance.offlinesync.service.StudentAttendanceOfflineSyncService;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.recording.service.StudentAttendanceRecordingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StudentAttendanceImportServiceImplTest {

    private StudentAttendanceImportPreviewRepository previewRepository;
    private AcademicYearRepository yearRepository;
    private GradeLevelRepository gradeRepository;
    private SectionRepository sectionRepository;
    private StudentAttendanceRecordingService recordingService;
    private StudentAttendanceOfflineSyncService offlineSyncService;
    private StudentAttendanceImportServiceImpl service;
    private ObjectMapper objectMapper;
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-12T10:00:00Z"), ZoneOffset.UTC
    );

    @BeforeEach
    void setUp() {
        previewRepository = mock(StudentAttendanceImportPreviewRepository.class);
        yearRepository = mock(AcademicYearRepository.class);
        gradeRepository = mock(GradeLevelRepository.class);
        sectionRepository = mock(SectionRepository.class);
        recordingService = mock(StudentAttendanceRecordingService.class);
        offlineSyncService = mock(StudentAttendanceOfflineSyncService.class);
        objectMapper = JsonMapper.builder().build();
        service = new StudentAttendanceImportServiceImpl(
                new StudentAttendanceImportFileParser(2_097_152),
                new StudentAttendanceImportFingerprintService(),
                previewRepository,
                yearRepository,
                gradeRepository,
                sectionRepository,
                recordingService,
                offlineSyncService,
                objectMapper,
                clock,
                transactionTemplate(),
                Duration.ofMinutes(30)
        );
    }

    @Test
    void previewNormalizesAndResolvesCompleteRosterWithoutWritingAttendance() {
        AcademicYear year = year();
        GradeLevel grade = grade();
        Section section = section();
        when(yearRepository.findByOrganizationIdAndNameIgnoreCaseAndStatus(
                7L, "2026-2027", AcademicYearStatus.ACTIVE))
                .thenReturn(Optional.of(year));
        when(gradeRepository.findByOrganizationIdAndAcademicYearIdAndCodeIgnoreCase(
                7L, 3L, "CLASS_1")).thenReturn(Optional.of(grade));
        when(sectionRepository.findByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndCodeIgnoreCase(
                7L, 3L, 4L, "A")).thenReturn(Optional.of(section));
        when(recordingService.previewOfflineDraft(
                7L, 11L, 3L, 4L, 5L, LocalDate.of(2026, 9, 11)
        )).thenReturn(new StudentAttendanceOfflineDraftSnapshot(
                2L,
                List.of(new StudentAttendanceOfflineDraftRosterEntry(
                        31L, 131L, "DR-001", 4L
                ))
        ));
        when(previewRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            StudentAttendanceImportPreview saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 8L);
            return saved;
        });

        StudentAttendanceImportPreviewResponse response = service.preview(
                7L, 11L, csv("DR-001", "PRESENT")
        );

        assertThat(response.canConfirm()).isTrue();
        assertThat(response.baseSessionVersion()).isEqualTo(2L);
        assertThat(response.rows().getFirst().studentEnrollmentId()).isEqualTo(31L);
        assertThat(response.rows().getFirst().expectedRecordVersion()).isEqualTo(4L);
        verifyNoInteractions(offlineSyncService);
    }

    @Test
    void previewReportsUnknownStudentAndCannotConfirm() {
        stubContext();
        when(recordingService.previewOfflineDraft(anyLong(), anyLong(), anyLong(),
                anyLong(), anyLong(), any())).thenReturn(
                new StudentAttendanceOfflineDraftSnapshot(
                        null,
                        List.of(new StudentAttendanceOfflineDraftRosterEntry(
                                31L, 131L, "DR-001", null
                        ))
                )
        );
        when(previewRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StudentAttendanceImportPreviewResponse response = service.preview(
                7L, 11L, csv("UNKNOWN", "PRESENT")
        );

        assertThat(response.canConfirm()).isFalse();
        assertThat(response.rows().getFirst().errors())
                .contains("Student roll number was not found in the section roster for this date");
    }

    @Test
    void confirmUsesStoredSnapshotAndRejectsDifferentKeyAfterSuccess() throws Exception {
        StudentAttendanceImportPreviewResponse storedPreview =
                new StudentAttendanceImportPreviewResponse(
                        null, "sha256:" + "a".repeat(64), "attendance.csv",
                        StudentAttendanceImportFormat.CSV, true,
                        3L, 4L, 5L, LocalDate.of(2026, 9, 11), 2L,
                        1, OffsetDateTime.now(clock).plusMinutes(30),
                        List.of(),
                        List.of(new StudentAttendanceImportRowResponse(
                                2, "2026-2027", "CLASS_1", "A",
                                LocalDate.of(2026, 9, 11), "DR-001",
                                31L, 4L, AttendanceStatus.PRESENT, null, List.of()
                        ))
                );
        String payload = objectMapper.writeValueAsString(
                new StoredStudentAttendanceImportPayload(storedPreview, List.of(31L))
        );
        StudentAttendanceImportPreview entity = new StudentAttendanceImportPreview(
                7L, 11L, 3L, 4L, 5L, LocalDate.of(2026, 9, 11),
                "sha256:" + "a".repeat(64), "attendance.csv",
                StudentAttendanceImportFormat.CSV, 1, true, payload,
                OffsetDateTime.now(clock).plusMinutes(30)
        );
        when(previewRepository.findForUpdate(8L, 7L, 11L))
                .thenReturn(Optional.of(entity));
        when(offlineSyncService.synchronize(eq(7L), eq(11L), eq("import-1"), any()))
                .thenReturn(new StudentAttendanceOfflineSyncResponse(
                        19L, "import-1", "sha256:" + "b".repeat(64),
                        false, null, List.of()
                ));

        service.confirm(
                7L, 11L, 8L, " import-1 ",
                new ConfirmStudentAttendanceImportRequest("sha256:" + "a".repeat(64))
        );

        assertThat(entity.getConfirmedSyncOperationId()).isEqualTo(19L);
        assertThatThrownBy(() -> service.confirm(
                7L, 11L, 8L, "import-2",
                new ConfirmStudentAttendanceImportRequest("sha256:" + "a".repeat(64))
        )).isInstanceOf(StudentAttendanceImportConflictException.class)
                .hasMessageContaining("another idempotency key");
    }

    private void stubContext() {
        when(yearRepository.findByOrganizationIdAndNameIgnoreCaseAndStatus(
                7L, "2026-2027", AcademicYearStatus.ACTIVE))
                .thenReturn(Optional.of(year()));
        when(gradeRepository.findByOrganizationIdAndAcademicYearIdAndCodeIgnoreCase(
                7L, 3L, "CLASS_1")).thenReturn(Optional.of(grade()));
        when(sectionRepository.findByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndCodeIgnoreCase(
                7L, 3L, 4L, "A")).thenReturn(Optional.of(section()));
    }

    private MockMultipartFile csv(String rollNumber, String status) {
        String csv = String.join(",", StudentAttendanceImportFileParser.HEADERS)
                + "\n2026-2027,CLASS_1,A,2026-09-11," + rollNumber
                + "," + status + ",\n";
        return new MockMultipartFile(
                "file", "attendance.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );
    }

    private AcademicYear year() {
        AcademicYear value = new AcademicYear(
                7L, "2026-2027",
                LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31)
        );
        ReflectionTestUtils.setField(value, "id", 3L);
        return value;
    }

    private GradeLevel grade() {
        GradeLevel value = new GradeLevel(7L, 3L, "CLASS_1", "Grade 1", 1);
        ReflectionTestUtils.setField(value, "id", 4L);
        return value;
    }

    private Section section() {
        Section value = new Section(7L, 3L, 4L, "A", "Section A", 1);
        ReflectionTestUtils.setField(value, "id", 5L);
        return value;
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override public void commit(TransactionStatus status) { }
            @Override public void rollback(TransactionStatus status) { }
        });
    }
}
