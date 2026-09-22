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
import com.dawnrise.academic.studentattendance.recording.dto.BulkStudentAttendanceRecordRequest;
import com.dawnrise.academic.studentattendance.recording.dto.StudentAttendanceSessionResponse;
import com.dawnrise.academic.studentattendance.recording.dto.SubmitStudentAttendanceRequest;
import com.dawnrise.academic.studentattendance.recording.exception.InvalidStudentAttendanceRecordingException;
import com.dawnrise.academic.studentattendance.recording.service.StudentAttendanceRecordingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.UnexpectedRollbackException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;
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
    void previewStoresValidationResultWhenTransactionalDraftPreviewThrows() {
        ParticipatingTransactionManager transactionManager =
                new ParticipatingTransactionManager();
        StudentAttendanceRecordingService transactionalRecordingService =
                transactionalProxy(
                        new FutureDateRecordingService(),
                        StudentAttendanceRecordingService.class,
                        transactionManager
                );
        StudentAttendanceImportService proxiedService = transactionalProxy(
                new StudentAttendanceImportServiceImpl(
                        new StudentAttendanceImportFileParser(2_097_152),
                        new StudentAttendanceImportFingerprintService(),
                        previewRepository,
                        yearRepository,
                        gradeRepository,
                        sectionRepository,
                        transactionalRecordingService,
                        offlineSyncService,
                        objectMapper,
                        clock,
                        new TransactionTemplate(transactionManager),
                        Duration.ofMinutes(30)
                ),
                StudentAttendanceImportService.class,
                transactionManager
        );
        when(yearRepository.findByOrganizationIdAndNameIgnoreCaseAndStatus(
                7L, "AY Session 2026-2027", AcademicYearStatus.ACTIVE))
                .thenReturn(Optional.of(year("AY Session 2026-2027")));
        when(gradeRepository.findByOrganizationIdAndAcademicYearIdAndCodeIgnoreCase(
                7L, 3L, "CLASS_1")).thenReturn(Optional.of(grade()));
        when(sectionRepository.findByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndCodeIgnoreCase(
                7L, 3L, 4L, "A")).thenReturn(Optional.of(section()));
        when(previewRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            StudentAttendanceImportPreview saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 12L);
            return saved;
        });

        StudentAttendanceImportPreviewResponse response = proxiedService.preview(
                7L, 11L, reproducingCsv()
        );

        assertThat(response.previewId()).isEqualTo(12L);
        assertThat(response.canConfirm()).isFalse();
        assertThat(response.errors()).contains("Attendance date cannot be in the future");
        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().getFirst().academicYear()).isEqualTo("AY Session 2026-2027");
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
        return year("2026-2027");
    }

    private AcademicYear year(String name) {
        AcademicYear value = new AcademicYear(
                7L, name,
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

    private MockMultipartFile reproducingCsv() {
        String csv = """
                academic_year,grade_code,section_code,attendance_date,roll_number,attendance_status,remarks
                AY Session 2026-2027,CLASS_1,A,2026-09-15,DR-001,PRESENT,Imported attendance test
                """;
        return new MockMultipartFile(
                "file", "attendance.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static <T> T transactionalProxy(
            T target,
            Class<T> serviceInterface,
            PlatformTransactionManager transactionManager
    ) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(
                new AnnotationTransactionAttributeSource()
        );
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTarget(target);
        proxyFactory.setInterfaces(serviceInterface);
        proxyFactory.addAdvice(interceptor);
        return serviceInterface.cast(proxyFactory.getProxy());
    }

    private static final class ParticipatingTransactionManager
            extends AbstractPlatformTransactionManager {

        private final ThreadLocal<TxObject> current = new ThreadLocal<>();

        @Override
        protected Object doGetTransaction() {
            TxObject existing = current.get();
            return existing == null ? new TxObject() : existing;
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((TxObject) transaction).active;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            TxObject tx = (TxObject) transaction;
            tx.active = true;
            tx.rollbackOnly = false;
            current.set(tx);
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            ((TxObject) status.getTransaction()).rollbackOnly = true;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (((TxObject) status.getTransaction()).rollbackOnly) {
                throw new UnexpectedRollbackException(
                        "Transaction silently rolled back because it has been marked as rollback-only"
                );
            }
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            TxObject tx = (TxObject) transaction;
            tx.active = false;
            current.remove();
        }
    }

    private static final class TxObject implements SmartTransactionObject {
        private boolean active;
        private boolean rollbackOnly;

        @Override
        public boolean isRollbackOnly() {
            return rollbackOnly;
        }

        @Override
        public void flush() {
        }
    }

    private static final class FutureDateRecordingService
            implements StudentAttendanceRecordingService {

        @Override
        @Transactional(readOnly = true)
        public StudentAttendanceOfflineDraftSnapshot previewOfflineDraft(
                long organizationId,
                long actorUserId,
                long academicYearId,
                long gradeLevelId,
                long sectionId,
                LocalDate attendanceDate
        ) {
            throw new InvalidStudentAttendanceRecordingException(
                    "Attendance date cannot be in the future"
            );
        }

        @Override
        public StudentAttendanceOfflineSyncAppliedResult synchronizeOfflineDraft(
                long organizationId,
                long actorUserId,
                StudentAttendanceOfflineSyncRequest request
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StudentAttendanceSessionResponse getOrCreateDraft(
                long organizationId,
                long actorUserId,
                long academicYearId,
                long gradeLevelId,
                long sectionId,
                LocalDate attendanceDate
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StudentAttendanceSessionResponse getSession(
                long organizationId,
                long actorUserId,
                long sessionId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StudentAttendanceSessionResponse saveDraftRecords(
                long organizationId,
                long actorUserId,
                long sessionId,
                BulkStudentAttendanceRecordRequest request
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StudentAttendanceSessionResponse submitManually(
                long organizationId,
                long actorUserId,
                long sessionId,
                SubmitStudentAttendanceRequest request
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StudentAttendanceSessionResponse getSectionAttendanceForDate(
                long organizationId,
                long actorUserId,
                long academicYearId,
                long gradeLevelId,
                long sectionId,
                LocalDate attendanceDate
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
