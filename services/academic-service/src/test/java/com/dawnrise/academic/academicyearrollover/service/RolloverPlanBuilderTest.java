package com.dawnrise.academic.academicyearrollover.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.academicyearrollover.dto.AcademicYearStructureRolloverRequest;
import com.dawnrise.academic.academicyearrollover.exception.InvalidRolloverRequestException;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.gradelevelsubject.repository.GradeLevelSubjectRepository;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.subject.entity.Subject;
import com.dawnrise.academic.subject.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RolloverPlanBuilderTest {

    private AcademicYearRepository academicYearRepository;
    private GradeLevelRepository gradeLevelRepository;
    private SectionRepository sectionRepository;
    private SubjectRepository subjectRepository;
    private GradeLevelSubjectRepository assignmentRepository;
    private RolloverPlanBuilder builder;

    @BeforeEach
    void setUp() {
        academicYearRepository = proxy(
                AcademicYearRepository.class,
                (method, args) -> {
                    if (method.equals("findByIdAndOrganizationId")
                            && args[0].equals(1L)) {
                        return Optional.of(year(
                                1L,
                                AcademicYearStatus.ACTIVE
                        ));
                    }
                    if (method.equals("findByIdAndOrganizationId")
                            && args[0].equals(2L)) {
                        return Optional.of(year(
                                2L,
                                AcademicYearStatus.PLANNED
                        ));
                    }
                    throw new UnsupportedOperationException(method);
                });
        gradeLevelRepository = proxy(
                GradeLevelRepository.class,
                (method, args) -> {
                    if (method.equals(
                            "findAllByOrganizationIdAndAcademicYearIdOrderByDisplayOrderAscIdAsc"
                    ) && args[1].equals(1L)) {
                        return List.of(grade(
                                11L,
                                1L,
                                "G1",
                                "Grade 1",
                                1
                        ));
                    }
                    if (method.equals(
                            "findAllByOrganizationIdAndAcademicYearIdOrderByDisplayOrderAscIdAsc"
                    ) && args[1].equals(2L)) {
                        return List.of(grade(
                                21L,
                                2L,
                                "G1",
                                "Grade 1",
                                1
                        ));
                    }
                    throw new UnsupportedOperationException(method);
                });
        sectionRepository = proxy(
                SectionRepository.class,
                (method, args) -> {
                    if (method.startsWith("findAllByOrganizationId")) {
                        return List.of();
                    }
                    throw new UnsupportedOperationException(method);
                });
        subjectRepository = proxy(
                SubjectRepository.class,
                (method, args) -> {
                    if (method.equals(
                            "findAllByOrganizationIdAndAcademicYearIdOrderByCodeAscIdAsc"
                    )) {
                        return List.of();
                    }
                    throw new UnsupportedOperationException(method);
                });
        assignmentRepository = proxy(
                GradeLevelSubjectRepository.class,
                (method, args) -> {
                    if (method.startsWith("findAllByOrganizationId")) {
                        return List.of();
                    }
                    throw new UnsupportedOperationException(method);
                });
        builder = new RolloverPlanBuilder(
                academicYearRepository,
                gradeLevelRepository,
                sectionRepository,
                subjectRepository,
                assignmentRepository,
                new RolloverFingerprintService()
        );
    }

    @Test
    void previewReturnsConflictsButBuildsPlan() {
        RolloverPlan plan = builder.build(
                10L,
                2L,
                new AcademicYearStructureRolloverRequest(
                        1L,
                        true,
                        false,
                        false,
                        false,
                        List.of(),
                        List.of()
                )
        );

        assertThat(plan.toPreviewResponse().canConfirm()).isFalse();
        assertThat(plan.conflicts()).hasSize(3);
        assertThat(plan.requestHash()).matches("sha256:[0-9a-f]{64}");
        assertThat(plan.previewFingerprint()).matches("sha256:[0-9a-f]{64}");
    }

    @Test
    void targetBeginningAfterSourceEndsIsAccepted() {
        RolloverPlan plan = builder.build(
                10L,
                2L,
                new AcademicYearStructureRolloverRequest(
                        1L,
                        true,
                        false,
                        false,
                        false,
                        List.of(),
                        List.of()
                )
        );

        assertThat(plan.sourceAcademicYear().getEndDate())
                .isBefore(plan.targetAcademicYear().getStartDate());
    }

    @Test
    void targetBeginningBeforeSourceIsRejected() {
        academicYearRepository = chronologicalRepository(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2024, 12, 1),
                LocalDate.of(2025, 11, 30)
        );
        builder = newBuilder();

        assertThatThrownBy(() -> builder.build(
                10L,
                2L,
                new AcademicYearStructureRolloverRequest(
                        1L,
                        true,
                        false,
                        false,
                        false,
                        List.of(),
                        List.of()
                )
        )).isInstanceOf(InvalidRolloverRequestException.class)
                .hasMessage("Target academic year must begin after the source academic year ends");
    }

    @Test
    void targetBeginningOnSourceEndDateIsRejected() {
        academicYearRepository = chronologicalRepository(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2026, 12, 30)
        );
        builder = newBuilder();

        assertThatThrownBy(() -> builder.build(
                10L,
                2L,
                new AcademicYearStructureRolloverRequest(
                        1L,
                        true,
                        false,
                        false,
                        false,
                        List.of(),
                        List.of()
                )
        )).isInstanceOf(InvalidRolloverRequestException.class)
                .hasMessage("Target academic year must begin after the source academic year ends");
    }

    @Test
    void invalidDependenciesAreRejected() {
        assertThatThrownBy(() -> builder.normalize(
                2L,
                new AcademicYearStructureRolloverRequest(
                        1L,
                        false,
                        true,
                        false,
                        false,
                        List.of(),
                        List.of()
                )
        )).isInstanceOf(InvalidRolloverRequestException.class)
                .hasMessage("Sections can only be copied when grade levels are copied");
    }

    @Test
    void plannedSourceIsRejected() {
        academicYearRepository = proxy(
                AcademicYearRepository.class,
                (method, args) -> Optional.of(year(
                        (Long) args[0],
                        AcademicYearStatus.PLANNED
                )));
        builder = new RolloverPlanBuilder(
                academicYearRepository,
                gradeLevelRepository,
                sectionRepository,
                subjectRepository,
                assignmentRepository,
                new RolloverFingerprintService()
        );

        assertThatThrownBy(() -> builder.build(
                10L,
                2L,
                new AcademicYearStructureRolloverRequest(
                        1L,
                        true,
                        false,
                        false,
                        false,
                        List.of(),
                        List.of()
                )
        )).isInstanceOf(InvalidRolloverRequestException.class)
                .hasMessage("Source academic year must be active or closed");
    }

    private AcademicYear year(Long id, AcademicYearStatus status) {
        LocalDate start = id.equals(1L)
                ? LocalDate.of(2025, 1, 1)
                : LocalDate.of(2026, 1, 1);
        LocalDate end = id.equals(1L)
                ? LocalDate.of(2025, 12, 31)
                : LocalDate.of(2026, 12, 31);
        return year(id, status, start, end);
    }

    private AcademicYear year(
            Long id,
            AcademicYearStatus status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        AcademicYear year = new AcademicYear(
                10L,
                "Year " + id,
                startDate,
                endDate
        );
        ReflectionTestUtils.setField(year, "id", id);
        ReflectionTestUtils.setField(year, "status", status);
        ReflectionTestUtils.setField(year, "version", 0L);
        return year;
    }

    private GradeLevel grade(
            Long id,
            Long academicYearId,
            String code,
            String name,
            Integer displayOrder
    ) {
        GradeLevel grade = new GradeLevel(
                10L,
                academicYearId,
                code,
                name,
                displayOrder
        );
        ReflectionTestUtils.setField(grade, "id", id);
        ReflectionTestUtils.setField(grade, "version", 0L);
        return grade;
    }

    private AcademicYearRepository chronologicalRepository(
            LocalDate sourceStart,
            LocalDate sourceEnd,
            LocalDate targetStart,
            LocalDate targetEnd
    ) {
        return proxy(
                AcademicYearRepository.class,
                (method, args) -> {
                    if (method.equals("findByIdAndOrganizationId")
                            && args[0].equals(1L)) {
                        return Optional.of(year(
                                1L,
                                AcademicYearStatus.ACTIVE,
                                sourceStart,
                                sourceEnd
                        ));
                    }
                    if (method.equals("findByIdAndOrganizationId")
                            && args[0].equals(2L)) {
                        return Optional.of(year(
                                2L,
                                AcademicYearStatus.PLANNED,
                                targetStart,
                                targetEnd
                        ));
                    }
                    throw new UnsupportedOperationException(method);
                });
    }

    private RolloverPlanBuilder newBuilder() {
        return new RolloverPlanBuilder(
                academicYearRepository,
                gradeLevelRepository,
                sectionRepository,
                subjectRepository,
                assignmentRepository,
                new RolloverFingerprintService()
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(
            Class<T> type,
            MethodHandler handler
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
                    if (method.getName().equals("toString")) {
                        return type.getSimpleName() + "Stub";
                    }
                    return handler.invoke(method.getName(), args);
                }
        );
    }

    private interface MethodHandler {
        Object invoke(String method, Object[] args);
    }
}
