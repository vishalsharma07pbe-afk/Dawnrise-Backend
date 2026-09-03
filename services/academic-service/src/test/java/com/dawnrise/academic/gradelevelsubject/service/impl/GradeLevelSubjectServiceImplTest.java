package com.dawnrise.academic.gradelevelsubject.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.exception.GradeLevelNotFoundException;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.gradelevelsubject.dto.CreateGradeLevelSubjectRequest;
import com.dawnrise.academic.gradelevelsubject.dto.GradeLevelSubjectResponse;
import com.dawnrise.academic.gradelevelsubject.dto.UpdateGradeLevelSubjectRequest;
import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import com.dawnrise.academic.gradelevelsubject.exception.GradeLevelSubjectConflictException;
import com.dawnrise.academic.gradelevelsubject.exception.GradeLevelSubjectNotFoundException;
import com.dawnrise.academic.gradelevelsubject.mapper.GradeLevelSubjectMapper;
import com.dawnrise.academic.gradelevelsubject.repository.GradeLevelSubjectRepository;
import com.dawnrise.academic.subject.entity.Subject;
import com.dawnrise.academic.subject.exception.SubjectNotFoundException;
import com.dawnrise.academic.subject.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradeLevelSubjectServiceImplTest {

    private static final long ORGANIZATION_ID = 10L;
    private static final long ACADEMIC_YEAR_ID = 20L;
    private static final long GRADE_LEVEL_ID = 30L;
    private static final long SUBJECT_ID = 40L;
    private static final long ASSIGNMENT_ID = 50L;

    private AcademicYearRepositoryStub academicYearRepositoryStub;
    private GradeLevelRepositoryStub gradeLevelRepositoryStub;
    private SubjectRepositoryStub subjectRepositoryStub;
    private AssignmentRepositoryStub assignmentRepositoryStub;
    private GradeLevelSubjectServiceImpl service;

    @BeforeEach
    void setUp() {
        academicYearRepositoryStub = new AcademicYearRepositoryStub();
        gradeLevelRepositoryStub = new GradeLevelRepositoryStub();
        subjectRepositoryStub = new SubjectRepositoryStub();
        assignmentRepositoryStub = new AssignmentRepositoryStub();
        academicYearRepositoryStub.foundAcademicYear =
                Optional.of(academicYear(AcademicYearStatus.PLANNED));
        gradeLevelRepositoryStub.foundGradeLevel = Optional.of(gradeLevel());
        subjectRepositoryStub.foundSubject = Optional.of(subject());
        service = new GradeLevelSubjectServiceImpl(
                academicYearRepositoryStub.repository(),
                gradeLevelRepositoryStub.repository(),
                subjectRepositoryStub.repository(),
                assignmentRepositoryStub.repository(),
                new GradeLevelSubjectMapper()
        );
    }

    @Test
    void assignSucceeds() {
        assignmentRepositoryStub.savedAssignment =
                assignment(ASSIGNMENT_ID, SUBJECT_ID, true, 1, 0L);

        GradeLevelSubjectResponse response = service.assign(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                new CreateGradeLevelSubjectRequest(SUBJECT_ID, true, 1)
        );

        assertThat(response.id()).isEqualTo(ASSIGNMENT_ID);
        assertThat(response.subjectId()).isEqualTo(SUBJECT_ID);
        assertThat(response.mandatory()).isTrue();
        assertThat(response.displayOrder()).isEqualTo(1);
        assertThat(assignmentRepositoryStub.lastSavedAssignment.getSubjectId())
                .isEqualTo(SUBJECT_ID);
    }

    @Test
    void getByIdUsesTenantSafeLookups() {
        assignmentRepositoryStub.foundAssignment =
                Optional.of(assignment(ASSIGNMENT_ID, SUBJECT_ID, true, 1, 0L));

        GradeLevelSubjectResponse response = service.getById(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                ASSIGNMENT_ID
        );

        assertThat(response.id()).isEqualTo(ASSIGNMENT_ID);
        assertThat(academicYearRepositoryStub.lastFindId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(academicYearRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
        assertThat(gradeLevelRepositoryStub.lastFindId).isEqualTo(GRADE_LEVEL_ID);
        assertThat(gradeLevelRepositoryStub.lastFindAcademicYearId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(gradeLevelRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
        assertThat(assignmentRepositoryStub.lastFindId).isEqualTo(ASSIGNMENT_ID);
        assertThat(assignmentRepositoryStub.lastFindGradeLevelId).isEqualTo(GRADE_LEVEL_ID);
        assertThat(assignmentRepositoryStub.lastFindAcademicYearId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(assignmentRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
    }

    @Test
    void assignUsesTenantSafeSubjectLookup() {
        service.assign(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                createRequest()
        );

        assertThat(subjectRepositoryStub.lastFindId).isEqualTo(SUBJECT_ID);
        assertThat(subjectRepositoryStub.lastFindAcademicYearId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(subjectRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
    }

    @Test
    void getAllMapsResultsInRepositoryOrder() {
        assignmentRepositoryStub.allAssignments = List.of(
                assignment(1L, SUBJECT_ID, true, 1, 0L),
                assignment(2L, 41L, false, 2, 0L)
        );

        List<GradeLevelSubjectResponse> responses = service.getAll(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID
        );

        assertThat(responses).extracting(GradeLevelSubjectResponse::displayOrder)
                .containsExactly(1, 2);
        assertThat(assignmentRepositoryStub.lastListOrganizationId)
                .isEqualTo(ORGANIZATION_ID);
        assertThat(assignmentRepositoryStub.lastListAcademicYearId)
                .isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(assignmentRepositoryStub.lastListGradeLevelId)
                .isEqualTo(GRADE_LEVEL_ID);
    }

    @Test
    void updateSucceedsWithoutReplacingSubject() {
        GradeLevelSubject existing =
                assignment(ASSIGNMENT_ID, SUBJECT_ID, true, 1, 3L);
        assignmentRepositoryStub.foundAssignment = Optional.of(existing);
        assignmentRepositoryStub.savedAssignment = existing;

        GradeLevelSubjectResponse response = service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                ASSIGNMENT_ID,
                new UpdateGradeLevelSubjectRequest(false, 2, 3L)
        );

        assertThat(response.subjectId()).isEqualTo(SUBJECT_ID);
        assertThat(response.mandatory()).isFalse();
        assertThat(response.displayOrder()).isEqualTo(2);
        assertThat(assignmentRepositoryStub.lastDuplicateExclusionId)
                .isEqualTo(ASSIGNMENT_ID);
    }

    @Test
    void removeDeletesAssignmentAndFlushes() {
        GradeLevelSubject existing =
                assignment(ASSIGNMENT_ID, SUBJECT_ID, true, 1, 3L);
        assignmentRepositoryStub.foundAssignment = Optional.of(existing);

        service.remove(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                ASSIGNMENT_ID
        );

        assertThat(assignmentRepositoryStub.deletedAssignment).isSameAs(existing);
        assertThat(assignmentRepositoryStub.flushCalls).isEqualTo(1);
    }

    @Test
    void missingParentsOrAssignmentAreRejected() {
        academicYearRepositoryStub.foundAcademicYear = Optional.empty();

        assertThatThrownBy(() -> service.getById(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                ASSIGNMENT_ID
        )).isInstanceOf(AcademicYearNotFoundException.class)
                .hasMessage("Academic year not found");

        academicYearRepositoryStub.foundAcademicYear =
                Optional.of(academicYear(AcademicYearStatus.PLANNED));
        gradeLevelRepositoryStub.foundGradeLevel = Optional.empty();

        assertThatThrownBy(() -> service.getById(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                ASSIGNMENT_ID
        )).isInstanceOf(GradeLevelNotFoundException.class)
                .hasMessage("Grade level not found");

        gradeLevelRepositoryStub.foundGradeLevel = Optional.of(gradeLevel());
        subjectRepositoryStub.foundSubject = Optional.empty();

        assertThatThrownBy(() -> service.assign(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                createRequest()
        )).isInstanceOf(SubjectNotFoundException.class)
                .hasMessage("Subject not found");

        subjectRepositoryStub.foundSubject = Optional.of(subject());
        assignmentRepositoryStub.foundAssignment = Optional.empty();

        assertThatThrownBy(() -> service.getById(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                ASSIGNMENT_ID
        )).isInstanceOf(GradeLevelSubjectNotFoundException.class)
                .hasMessage("Grade-subject assignment not found");
    }

    @Test
    void duplicateSubjectAssignmentAndDisplayOrderAreRejectedOnAssign() {
        assignmentRepositoryStub.duplicateSubject = true;

        assertThatThrownBy(() -> service.assign(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                createRequest()
        )).isInstanceOf(GradeLevelSubjectConflictException.class)
                .hasMessage("This subject is already assigned to the grade level");

        assignmentRepositoryStub.duplicateSubject = false;
        assignmentRepositoryStub.duplicateDisplayOrder = true;

        assertThatThrownBy(() -> service.assign(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                createRequest()
        )).isInstanceOf(GradeLevelSubjectConflictException.class)
                .hasMessage("This display order is already used by another subject");
    }

    @Test
    void duplicateDisplayOrderIsRejectedOnUpdate() {
        assignmentRepositoryStub.foundAssignment =
                Optional.of(assignment(ASSIGNMENT_ID, SUBJECT_ID, true, 1, 3L));
        assignmentRepositoryStub.duplicateDisplayOrder = true;

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                ASSIGNMENT_ID,
                updateRequest(3L)
        )).isInstanceOf(GradeLevelSubjectConflictException.class)
                .hasMessage("This display order is already used by another subject");
    }

    @Test
    void updateRejectsStaleVersion() {
        assignmentRepositoryStub.foundAssignment =
                Optional.of(assignment(ASSIGNMENT_ID, SUBJECT_ID, true, 1, 3L));

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                ASSIGNMENT_ID,
                updateRequest(2L)
        )).isInstanceOf(GradeLevelSubjectConflictException.class)
                .hasMessage("Grade-subject assignment was modified by another request");
    }

    @Test
    void modificationsAreAllowedForPlannedAndActiveYears() {
        for (AcademicYearStatus status : List.of(
                AcademicYearStatus.PLANNED,
                AcademicYearStatus.ACTIVE
        )) {
            academicYearRepositoryStub.foundAcademicYear =
                    Optional.of(academicYear(status));
            assignmentRepositoryStub.savedAssignment =
                    assignment(ASSIGNMENT_ID, SUBJECT_ID, true, 1, 0L);

            assertThat(service.assign(
                    ORGANIZATION_ID,
                    ACADEMIC_YEAR_ID,
                    GRADE_LEVEL_ID,
                    createRequest()
            ).id()).isEqualTo(ASSIGNMENT_ID);

            GradeLevelSubject existing =
                    assignment(ASSIGNMENT_ID, SUBJECT_ID, true, 1, 3L);
            assignmentRepositoryStub.foundAssignment = Optional.of(existing);
            assignmentRepositoryStub.savedAssignment = existing;

            assertThat(service.update(
                    ORGANIZATION_ID,
                    ACADEMIC_YEAR_ID,
                    GRADE_LEVEL_ID,
                    ASSIGNMENT_ID,
                    updateRequest(3L)
            ).id()).isEqualTo(ASSIGNMENT_ID);

            service.remove(
                    ORGANIZATION_ID,
                    ACADEMIC_YEAR_ID,
                    GRADE_LEVEL_ID,
                    ASSIGNMENT_ID
            );
        }
    }

    @Test
    void modificationsAreRejectedForClosedAndVoidedYears() {
        for (AcademicYearStatus status : List.of(
                AcademicYearStatus.CLOSED,
                AcademicYearStatus.VOIDED
        )) {
            academicYearRepositoryStub.foundAcademicYear =
                    Optional.of(academicYear(status));

            assertThatThrownBy(() -> service.assign(
                    ORGANIZATION_ID,
                    ACADEMIC_YEAR_ID,
                    GRADE_LEVEL_ID,
                    createRequest()
            )).isInstanceOf(GradeLevelSubjectConflictException.class)
                    .hasMessage("Subject assignments can only be modified for a planned or active academic year");

            assertThatThrownBy(() -> service.update(
                    ORGANIZATION_ID,
                    ACADEMIC_YEAR_ID,
                    GRADE_LEVEL_ID,
                    ASSIGNMENT_ID,
                    updateRequest(3L)
            )).isInstanceOf(GradeLevelSubjectConflictException.class)
                    .hasMessage("Subject assignments can only be modified for a planned or active academic year");

            assertThatThrownBy(() -> service.remove(
                    ORGANIZATION_ID,
                    ACADEMIC_YEAR_ID,
                    GRADE_LEVEL_ID,
                    ASSIGNMENT_ID
            )).isInstanceOf(GradeLevelSubjectConflictException.class)
                    .hasMessage("Subject assignments can only be modified for a planned or active academic year");
        }
    }

    private static CreateGradeLevelSubjectRequest createRequest() {
        return new CreateGradeLevelSubjectRequest(SUBJECT_ID, true, 1);
    }

    private static UpdateGradeLevelSubjectRequest updateRequest(Long version) {
        return new UpdateGradeLevelSubjectRequest(true, 1, version);
    }

    private static AcademicYear academicYear(AcademicYearStatus status) {
        AcademicYear academicYear = new AcademicYear(
                ORGANIZATION_ID,
                "2026-2027",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2027, 3, 31)
        );
        ReflectionTestUtils.setField(academicYear, "id", ACADEMIC_YEAR_ID);
        ReflectionTestUtils.setField(academicYear, "status", status);
        return academicYear;
    }

    private static GradeLevel gradeLevel() {
        GradeLevel gradeLevel = new GradeLevel(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                "G1",
                "Grade 1",
                1
        );
        ReflectionTestUtils.setField(gradeLevel, "id", GRADE_LEVEL_ID);
        return gradeLevel;
    }

    private static Subject subject() {
        Subject subject = new Subject(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                "MATH",
                "Mathematics",
                null
        );
        ReflectionTestUtils.setField(subject, "id", SUBJECT_ID);
        return subject;
    }

    private static GradeLevelSubject assignment(
            Long id,
            Long subjectId,
            Boolean mandatory,
            Integer displayOrder,
            Long version
    ) {
        GradeLevelSubject assignment = new GradeLevelSubject(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                subjectId,
                mandatory,
                displayOrder
        );
        ReflectionTestUtils.setField(assignment, "id", id);
        ReflectionTestUtils.setField(assignment, "version", version);
        ReflectionTestUtils.setField(
                assignment,
                "createdAt",
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
        ReflectionTestUtils.setField(
                assignment,
                "updatedAt",
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
        return assignment;
    }

    private static class AcademicYearRepositoryStub {

        private Optional<AcademicYear> foundAcademicYear = Optional.empty();
        private long lastFindId;
        private long lastFindOrganizationId;

        private AcademicYearRepository repository() {
            return (AcademicYearRepository) Proxy.newProxyInstance(
                    AcademicYearRepository.class.getClassLoader(),
                    new Class<?>[]{AcademicYearRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByIdAndOrganizationId" -> {
                            lastFindId = (Long) args[0];
                            lastFindOrganizationId = (Long) args[1];
                            yield foundAcademicYear;
                        }
                        case "toString" -> "AcademicYearRepositoryStub";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static class GradeLevelRepositoryStub {

        private Optional<GradeLevel> foundGradeLevel = Optional.empty();
        private long lastFindId;
        private long lastFindAcademicYearId;
        private long lastFindOrganizationId;

        private GradeLevelRepository repository() {
            return (GradeLevelRepository) Proxy.newProxyInstance(
                    GradeLevelRepository.class.getClassLoader(),
                    new Class<?>[]{GradeLevelRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByIdAndAcademicYearIdAndOrganizationId" -> {
                            lastFindId = (Long) args[0];
                            lastFindAcademicYearId = (Long) args[1];
                            lastFindOrganizationId = (Long) args[2];
                            yield foundGradeLevel;
                        }
                        case "toString" -> "GradeLevelRepositoryStub";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static class SubjectRepositoryStub {

        private Optional<Subject> foundSubject = Optional.empty();
        private long lastFindId;
        private long lastFindAcademicYearId;
        private long lastFindOrganizationId;

        private SubjectRepository repository() {
            return (SubjectRepository) Proxy.newProxyInstance(
                    SubjectRepository.class.getClassLoader(),
                    new Class<?>[]{SubjectRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByIdAndAcademicYearIdAndOrganizationId" -> {
                            lastFindId = (Long) args[0];
                            lastFindAcademicYearId = (Long) args[1];
                            lastFindOrganizationId = (Long) args[2];
                            yield foundSubject;
                        }
                        case "toString" -> "SubjectRepositoryStub";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static class AssignmentRepositoryStub {

        private Optional<GradeLevelSubject> foundAssignment = Optional.empty();
        private List<GradeLevelSubject> allAssignments = List.of();
        private GradeLevelSubject savedAssignment;
        private GradeLevelSubject lastSavedAssignment;
        private GradeLevelSubject deletedAssignment;
        private long lastFindId;
        private long lastFindGradeLevelId;
        private long lastFindAcademicYearId;
        private long lastFindOrganizationId;
        private long lastListOrganizationId;
        private long lastListAcademicYearId;
        private long lastListGradeLevelId;
        private long lastDuplicateExclusionId;
        private boolean duplicateSubject;
        private boolean duplicateDisplayOrder;
        private int flushCalls;

        private GradeLevelSubjectRepository repository() {
            return (GradeLevelSubjectRepository) Proxy.newProxyInstance(
                    GradeLevelSubjectRepository.class.getClassLoader(),
                    new Class<?>[]{GradeLevelSubjectRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId" -> {
                            lastFindId = (Long) args[0];
                            lastFindGradeLevelId = (Long) args[1];
                            lastFindAcademicYearId = (Long) args[2];
                            lastFindOrganizationId = (Long) args[3];
                            yield foundAssignment;
                        }
                        case "findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdOrderByDisplayOrderAsc" -> {
                            lastListOrganizationId = (Long) args[0];
                            lastListAcademicYearId = (Long) args[1];
                            lastListGradeLevelId = (Long) args[2];
                            yield allAssignments;
                        }
                        case "existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSubjectId" -> duplicateSubject;
                        case "existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndDisplayOrder" -> duplicateDisplayOrder;
                        case "existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndDisplayOrderAndIdNot" -> {
                            lastDuplicateExclusionId = (Long) args[4];
                            yield duplicateDisplayOrder;
                        }
                        case "saveAndFlush" -> {
                            lastSavedAssignment = (GradeLevelSubject) args[0];
                            yield savedAssignment != null
                                    ? savedAssignment
                                    : lastSavedAssignment;
                        }
                        case "delete" -> {
                            deletedAssignment = (GradeLevelSubject) args[0];
                            yield null;
                        }
                        case "flush" -> {
                            flushCalls++;
                            yield null;
                        }
                        case "toString" -> "GradeLevelSubjectRepositoryStub";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
