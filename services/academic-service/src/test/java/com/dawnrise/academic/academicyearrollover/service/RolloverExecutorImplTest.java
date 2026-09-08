package com.dawnrise.academic.academicyearrollover.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.academicyearrollover.dto.*;
import com.dawnrise.academic.academicyearrollover.entity.AcademicYearStructureRolloverOperation;
import com.dawnrise.academic.academicyearrollover.enums.RolloverOperationStatus;
import com.dawnrise.academic.academicyearrollover.exception.RolloverConflictException;
import com.dawnrise.academic.academicyearrollover.exception.StaleRolloverPreviewException;
import com.dawnrise.academic.academicyearrollover.repository.AcademicYearStructureRolloverOperationRepository;
import com.dawnrise.academic.academicyearrollover.service.impl.RolloverExecutorImpl;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import com.dawnrise.academic.gradelevelsubject.repository.GradeLevelSubjectRepository;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.subject.entity.Subject;
import com.dawnrise.academic.subject.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RolloverExecutorImplTest {

    private OperationRepositoryStub operationRepository;
    private AcademicYearRepositoryStub academicYearRepository;
    private SavingRepositoryStub savingRepository;
    private StubPlanBuilder planBuilder;
    private RolloverExecutorImpl executor;

    @BeforeEach
    void setUp() {
        operationRepository = new OperationRepositoryStub();
        academicYearRepository = new AcademicYearRepositoryStub();
        savingRepository = new SavingRepositoryStub();
        planBuilder = new StubPlanBuilder();
        executor = new RolloverExecutorImpl(
                operationRepository.repository(),
                academicYearRepository.repository(),
                planBuilder,
                savingRepository.subjectRepository(),
                savingRepository.gradeLevelRepository(),
                savingRepository.sectionRepository(),
                savingRepository.assignmentRepository(),
                JsonMapper.builder().build()
        );
    }

    @Test
    void locksOperationAndTargetThenPersistsSucceededAtomically() {
        AcademicYearStructureRolloverResultResponse response = executor.execute(
                10L,
                100L,
                hash('b'),
                request()
        );

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(operationRepository.lastLockedOrganizationId).isEqualTo(10L);
        assertThat(academicYearRepository.lastLockedId).isEqualTo(2L);
        assertThat(savingRepository.savedSubjects).hasSize(1);
        assertThat(savingRepository.savedGrades).hasSize(1);
        assertThat(savingRepository.savedSections).hasSize(1);
        assertThat(savingRepository.savedAssignments).hasSize(1);
        assertThat(savingRepository.studentEnrollmentSaveCalls).isZero();
        assertThat(savingRepository.teacherAssignmentSaveCalls).isZero();
        assertThat(operationRepository.saved.getStatus())
                .isEqualTo(RolloverOperationStatus.SUCCEEDED);
        assertThat(operationRepository.saved.getResultJson()).isNotBlank();
    }

    @Test
    void stalePreviewAfterSourceChangeIsRejectedBeforeWrites() {
        planBuilder.requestHash = hash('c');

        assertThatThrownBy(() -> executor.execute(
                10L,
                100L,
                hash('b'),
                request()
        )).isInstanceOf(StaleRolloverPreviewException.class)
                .hasMessage("Rollover request no longer matches the registered operation");

        assertThat(savingRepository.totalSaveCalls()).isZero();
    }

    @Test
    void stalePreviewAfterTargetChangeIsRejectedBeforeWrites() {
        planBuilder.previewFingerprint = hash('c');

        assertThatThrownBy(() -> executor.execute(
                10L,
                100L,
                hash('b'),
                request()
        )).isInstanceOf(StaleRolloverPreviewException.class)
                .hasMessage("Rollover preview is stale");

        assertThat(savingRepository.totalSaveCalls()).isZero();
    }

    @Test
    void conflictPlanIsRejectedBeforeWritesAndCanBeStoredAsConflicted() {
        planBuilder.conflicts = List.of(new RolloverConflictResponse(
                "TARGET_SUBJECT_CODE_EXISTS",
                "SUBJECT",
                12L,
                "code",
                "MATH",
                "Target conflict"
        ));

        assertThatThrownBy(() -> executor.execute(
                10L,
                100L,
                hash('b'),
                request()
        )).isInstanceOf(RolloverConflictException.class)
                .hasMessage("Target academic year contains conflicting structure");

        assertThat(savingRepository.totalSaveCalls()).isZero();
    }

    @Test
    void rollbackAtSubjectCreationLeavesOperationRunningInTransaction() {
        savingRepository.failSubjectSave = true;

        assertThatThrownBy(() -> executor.execute(
                10L,
                100L,
                hash('b'),
                request()
        )).isInstanceOf(RolloverConflictException.class)
                .hasMessage("Academic structure changed during rollover confirmation");

        assertThat(operationRepository.operation.getStatus())
                .isEqualTo(RolloverOperationStatus.RUNNING);
        assertThat(operationRepository.saveAndFlushCalls).isZero();
    }

    @Test
    void rollbackAtGradeCreationLeavesOperationRunningInTransaction() {
        savingRepository.failGradeSave = true;

        assertThatThrownBy(() -> executor.execute(
                10L,
                100L,
                hash('b'),
                request()
        )).isInstanceOf(RolloverConflictException.class);

        assertThat(operationRepository.operation.getStatus())
                .isEqualTo(RolloverOperationStatus.RUNNING);
        assertThat(operationRepository.saveAndFlushCalls).isZero();
    }

    @Test
    void rollbackAtSectionCreationLeavesOperationRunningInTransaction() {
        savingRepository.failSectionSave = true;

        assertThatThrownBy(() -> executor.execute(
                10L,
                100L,
                hash('b'),
                request()
        )).isInstanceOf(RolloverConflictException.class);

        assertThat(operationRepository.operation.getStatus())
                .isEqualTo(RolloverOperationStatus.RUNNING);
        assertThat(operationRepository.saveAndFlushCalls).isZero();
    }

    @Test
    void rollbackAtGradeSubjectCreationLeavesOperationRunningInTransaction() {
        savingRepository.failAssignmentSave = true;

        assertThatThrownBy(() -> executor.execute(
                10L,
                100L,
                hash('b'),
                request()
        )).isInstanceOf(RolloverConflictException.class);

        assertThat(operationRepository.operation.getStatus())
                .isEqualTo(RolloverOperationStatus.RUNNING);
        assertThat(operationRepository.saveAndFlushCalls).isZero();
    }

    @Test
    void generatedRecordLimitIsEnforcedByPlanBuilderConstant() {
        assertThat(RolloverPlanBuilder.MAX_GENERATED_STRUCTURE_RECORDS)
                .isEqualTo(2_000);
    }

    private static AcademicYearStructureRolloverRequest request() {
        return new AcademicYearStructureRolloverRequest(
                1L, true, true, true, true, List.of(), List.of()
        );
    }

    private static RolloverPlan plan(
            String requestHash,
            String previewFingerprint,
            List<RolloverConflictResponse> conflicts
    ) {
        GradeLevel grade = grade(11L, 1L);
        Subject subject = subject(12L, 1L);
        return new RolloverPlan(
                new RolloverOptions(
                        1L,
                        2L,
                        true,
                        true,
                        true,
                        true,
                        List.of(),
                        List.of()
                ),
                year(1L, AcademicYearStatus.ACTIVE),
                year(2L, AcademicYearStatus.PLANNED),
                List.of(grade),
                List.of(section(13L, 1L, grade.getId())),
                List.of(subject),
                List.of(assignment(14L, 1L, grade.getId(), subject.getId())),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                conflicts,
                requestHash,
                previewFingerprint
        );
    }

    private static AcademicYear year(Long id, AcademicYearStatus status) {
        AcademicYear year = new AcademicYear(
                10L,
                "Year " + id,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31)
        );
        ReflectionTestUtils.setField(year, "id", id);
        ReflectionTestUtils.setField(year, "status", status);
        ReflectionTestUtils.setField(year, "version", 0L);
        return year;
    }

    private static GradeLevel grade(Long id, Long academicYearId) {
        GradeLevel grade = new GradeLevel(
                10L, academicYearId, "G1", "Grade 1", 1
        );
        ReflectionTestUtils.setField(grade, "id", id);
        ReflectionTestUtils.setField(grade, "version", 0L);
        return grade;
    }

    private static Subject subject(Long id, Long academicYearId) {
        Subject subject = new Subject(
                10L, academicYearId, "MATH", "Math", "Core"
        );
        ReflectionTestUtils.setField(subject, "id", id);
        ReflectionTestUtils.setField(subject, "version", 0L);
        return subject;
    }

    private static Section section(
            Long id,
            Long academicYearId,
            Long gradeLevelId
    ) {
        Section section = new Section(
                10L, academicYearId, gradeLevelId, "A", "A", 1
        );
        ReflectionTestUtils.setField(section, "id", id);
        ReflectionTestUtils.setField(section, "version", 0L);
        return section;
    }

    private static GradeLevelSubject assignment(
            Long id,
            Long academicYearId,
            Long gradeLevelId,
            Long subjectId
    ) {
        GradeLevelSubject assignment = new GradeLevelSubject(
                10L, academicYearId, gradeLevelId, subjectId, true, 1
        );
        ReflectionTestUtils.setField(assignment, "id", id);
        ReflectionTestUtils.setField(assignment, "version", 0L);
        return assignment;
    }

    private static String hash(char value) {
        return "sha256:" + String.valueOf(value).repeat(64);
    }

    private static class StubPlanBuilder extends RolloverPlanBuilder {
        String requestHash = hash('a');
        String previewFingerprint = hash('b');
        List<RolloverConflictResponse> conflicts = List.of();

        StubPlanBuilder() {
            super(null, null, null, null, null, new RolloverFingerprintService());
        }

        @Override
        public RolloverPlan build(
                long organizationId,
                long targetAcademicYearId,
                AcademicYearStructureRolloverRequest request
        ) {
            return plan(requestHash, previewFingerprint, conflicts);
        }
    }

    private static class OperationRepositoryStub {
        final AcademicYearStructureRolloverOperation operation =
                new AcademicYearStructureRolloverOperation(
                        10L, 1L, 2L, "key-1", hash('a'), hash('b'), 42L
                );
        AcademicYearStructureRolloverOperation saved;
        int saveAndFlushCalls;
        Long lastLockedOrganizationId;

        OperationRepositoryStub() {
            ReflectionTestUtils.setField(operation, "id", 100L);
            operation.markRunning();
        }

        AcademicYearStructureRolloverOperationRepository repository() {
            return (AcademicYearStructureRolloverOperationRepository)
                    Proxy.newProxyInstance(
                            AcademicYearStructureRolloverOperationRepository.class
                                    .getClassLoader(),
                            new Class<?>[]{
                                    AcademicYearStructureRolloverOperationRepository.class
                            },
                            (proxy, method, args) -> {
                                if (method.getName().equals("hashCode")) {
                                    return System.identityHashCode(proxy);
                                }
                                if (method.getName().equals("equals")) {
                                    return proxy == args[0];
                                }
                                if (method.getName().equals(
                                        "findByIdAndOrganizationIdForUpdate"
                                )) {
                                    lastLockedOrganizationId = (Long) args[1];
                                    return Optional.of(operation);
                                }
                                if (method.getName().equals("saveAndFlush")) {
                                    saveAndFlushCalls++;
                                    saved = (AcademicYearStructureRolloverOperation)
                                            args[0];
                                    return saved;
                                }
                                throw new UnsupportedOperationException(
                                        method.getName()
                                );
                            }
                    );
        }
    }

    private static class AcademicYearRepositoryStub {
        Long lastLockedId;

        AcademicYearRepository repository() {
            return (AcademicYearRepository) Proxy.newProxyInstance(
                    AcademicYearRepository.class.getClassLoader(),
                    new Class<?>[]{AcademicYearRepository.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        }
                        if (method.getName().equals("equals")) {
                            return proxy == args[0];
                        }
                        if (method.getName().equals(
                                "findByIdAndOrganizationIdForUpdate"
                        )) {
                            lastLockedId = (Long) args[0];
                            return Optional.of(year(
                                    (Long) args[0],
                                    AcademicYearStatus.PLANNED
                            ));
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static class SavingRepositoryStub {
        final List<Subject> savedSubjects = new ArrayList<>();
        final List<GradeLevel> savedGrades = new ArrayList<>();
        final List<Section> savedSections = new ArrayList<>();
        final List<GradeLevelSubject> savedAssignments = new ArrayList<>();
        boolean failSubjectSave;
        boolean failGradeSave;
        boolean failSectionSave;
        boolean failAssignmentSave;
        int studentEnrollmentSaveCalls;
        int teacherAssignmentSaveCalls;

        int totalSaveCalls() {
            return savedSubjects.size()
                    + savedGrades.size()
                    + savedSections.size()
                    + savedAssignments.size();
        }

        SubjectRepository subjectRepository() {
            return proxy(
                    SubjectRepository.class,
                    "saveAll",
                    savedSubjects,
                    201L
            );
        }

        GradeLevelRepository gradeLevelRepository() {
            return proxy(
                    GradeLevelRepository.class,
                    "saveAll",
                    savedGrades,
                    301L
            );
        }

        SectionRepository sectionRepository() {
            return proxy(
                    SectionRepository.class,
                    "saveAll",
                    savedSections,
                    401L
            );
        }

        GradeLevelSubjectRepository assignmentRepository() {
            return proxy(
                    GradeLevelSubjectRepository.class,
                    "saveAll",
                    savedAssignments,
                    501L
            );
        }

        @SuppressWarnings("unchecked")
        private <T> T proxy(
                Class<T> type,
                String saveMethod,
                List<?> saved,
                long firstId
        ) {
            return (T) Proxy.newProxyInstance(
                    type.getClassLoader(),
                    new Class<?>[]{type},
                    (proxy, method, args) -> {
                        if (method.getName().equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        }
                        if (method.getName().equals("equals")) {
                            return proxy == args[0];
                        }
                        if (method.getName().equals(saveMethod)) {
                            if (shouldFail(type)) {
                                throw new DataIntegrityViolationException(
                                        "duplicate key"
                                );
                            }
                            List<Object> input = (List<Object>) args[0];
                            for (int i = 0; i < input.size(); i++) {
                                ReflectionTestUtils.setField(
                                        input.get(i),
                                        "id",
                                        firstId + i
                                );
                            }
                            ((List<Object>) saved).addAll(input);
                            return input;
                        }
                        if (method.getName().equals("flush")) {
                            return null;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private boolean shouldFail(Class<?> type) {
            if (type == SubjectRepository.class) {
                return failSubjectSave;
            }
            if (type == GradeLevelRepository.class) {
                return failGradeSave;
            }
            if (type == SectionRepository.class) {
                return failSectionSave;
            }
            return failAssignmentSave;
        }
    }
}
