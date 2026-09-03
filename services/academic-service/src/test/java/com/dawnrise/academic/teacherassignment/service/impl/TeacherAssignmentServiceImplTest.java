package com.dawnrise.academic.teacherassignment.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.exception.GradeLevelNotFoundException;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import com.dawnrise.academic.gradelevelsubject.exception.GradeLevelSubjectNotFoundException;
import com.dawnrise.academic.gradelevelsubject.repository.GradeLevelSubjectRepository;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.section.exception.SectionNotFoundException;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.teacherassignment.dto.CreateTeacherAssignmentRequest;
import com.dawnrise.academic.teacherassignment.dto.TeacherAssignmentResponse;
import com.dawnrise.academic.teacherassignment.dto.UpdateTeacherAssignmentRequest;
import com.dawnrise.academic.teacherassignment.entity.TeacherAssignment;
import com.dawnrise.academic.teacherassignment.enums.TeacherAssignmentType;
import com.dawnrise.academic.teacherassignment.exception.TeacherAssignmentConflictException;
import com.dawnrise.academic.teacherassignment.exception.TeacherAssignmentNotFoundException;
import com.dawnrise.academic.teacherassignment.exception.TeacherNotEligibleException;
import com.dawnrise.academic.teacherassignment.integration.identity.IdentityTeacherEligibilityClient;
import com.dawnrise.academic.teacherassignment.integration.identity.IdentityTeacherEligibilityException;
import com.dawnrise.academic.teacherassignment.integration.identity.TeachingEligibilityReason;
import com.dawnrise.academic.teacherassignment.integration.identity.TeachingEligibilityResponse;
import com.dawnrise.academic.teacherassignment.mapper.TeacherAssignmentMapper;
import com.dawnrise.academic.teacherassignment.repository.TeacherAssignmentRepository;
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

class TeacherAssignmentServiceImplTest {

    private static final long ORGANIZATION_ID = 10L;
    private static final long ACADEMIC_YEAR_ID = 20L;
    private static final long GRADE_LEVEL_ID = 30L;
    private static final long SECTION_ID = 40L;
    private static final long GRADE_SUBJECT_ID = 50L;
    private static final long TEACHER_USER_ID = 60L;
    private static final long ASSIGNMENT_ID = 70L;

    private AcademicYearRepositoryStub academicYearRepositoryStub;
    private GradeLevelRepositoryStub gradeLevelRepositoryStub;
    private SectionRepositoryStub sectionRepositoryStub;
    private GradeSubjectRepositoryStub gradeSubjectRepositoryStub;
    private AssignmentRepositoryStub assignmentRepositoryStub;
    private EligibilityClientStub eligibilityClientStub;
    private TeacherAssignmentServiceImpl service;

    @BeforeEach
    void setUp() {
        academicYearRepositoryStub = new AcademicYearRepositoryStub();
        gradeLevelRepositoryStub = new GradeLevelRepositoryStub();
        sectionRepositoryStub = new SectionRepositoryStub();
        gradeSubjectRepositoryStub = new GradeSubjectRepositoryStub();
        assignmentRepositoryStub = new AssignmentRepositoryStub();
        eligibilityClientStub = new EligibilityClientStub();
        academicYearRepositoryStub.foundAcademicYear =
                Optional.of(academicYear(AcademicYearStatus.PLANNED));
        gradeLevelRepositoryStub.foundGradeLevel = Optional.of(gradeLevel());
        sectionRepositoryStub.foundSection = Optional.of(section());
        gradeSubjectRepositoryStub.foundGradeSubject =
                Optional.of(gradeSubject());
        eligibilityClientStub.response = eligible(TEACHER_USER_ID);
        service = new TeacherAssignmentServiceImpl(
                academicYearRepositoryStub.repository(),
                gradeLevelRepositoryStub.repository(),
                sectionRepositoryStub.repository(),
                gradeSubjectRepositoryStub.repository(),
                assignmentRepositoryStub.repository(),
                eligibilityClientStub,
                new TeacherAssignmentMapper()
        );
    }

    @Test
    void createClassTeacherSucceeds() {
        assignmentRepositoryStub.savedAssignment =
                assignment(ASSIGNMENT_ID, null, TEACHER_USER_ID, TeacherAssignmentType.CLASS_TEACHER, 0L);

        TeacherAssignmentResponse response = service.create(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                classTeacherRequest(TEACHER_USER_ID)
        );

        assertThat(response.id()).isEqualTo(ASSIGNMENT_ID);
        assertThat(response.gradeLevelSubjectId()).isNull();
        assertThat(response.assignmentType())
                .isEqualTo(TeacherAssignmentType.CLASS_TEACHER);
        assertThat(assignmentRepositoryStub.lastSavedAssignment.getTeacherUserId())
                .isEqualTo(TEACHER_USER_ID);
    }

    @Test
    void createSubjectTeacherSucceeds() {
        assignmentRepositoryStub.savedAssignment =
                assignment(ASSIGNMENT_ID, GRADE_SUBJECT_ID, TEACHER_USER_ID, TeacherAssignmentType.SUBJECT_TEACHER, 0L);

        TeacherAssignmentResponse response = service.create(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                subjectTeacherRequest(TEACHER_USER_ID)
        );

        assertThat(response.gradeLevelSubjectId()).isEqualTo(GRADE_SUBJECT_ID);
        assertThat(response.assignmentType())
                .isEqualTo(TeacherAssignmentType.SUBJECT_TEACHER);
        assertThat(gradeSubjectRepositoryStub.lastFindId)
                .isEqualTo(GRADE_SUBJECT_ID);
    }

    @Test
    void createUsesTenantSafeParentAndGradeSubjectValidation() {
        service.create(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                subjectTeacherRequest(TEACHER_USER_ID)
        );

        assertThat(academicYearRepositoryStub.lastFindId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(academicYearRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
        assertThat(gradeLevelRepositoryStub.lastFindId).isEqualTo(GRADE_LEVEL_ID);
        assertThat(gradeLevelRepositoryStub.lastFindAcademicYearId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(gradeLevelRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
        assertThat(sectionRepositoryStub.lastFindId).isEqualTo(SECTION_ID);
        assertThat(sectionRepositoryStub.lastFindGradeLevelId).isEqualTo(GRADE_LEVEL_ID);
        assertThat(sectionRepositoryStub.lastFindAcademicYearId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(sectionRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
        assertThat(gradeSubjectRepositoryStub.lastFindGradeLevelId).isEqualTo(GRADE_LEVEL_ID);
        assertThat(gradeSubjectRepositoryStub.lastFindAcademicYearId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(gradeSubjectRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
    }

    @Test
    void missingParentsAreRejected() {
        academicYearRepositoryStub.foundAcademicYear = Optional.empty();
        assertThatThrownBy(() -> service.create(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, classTeacherRequest(TEACHER_USER_ID)))
                .isInstanceOf(AcademicYearNotFoundException.class)
                .hasMessage("Academic year not found");

        academicYearRepositoryStub.foundAcademicYear =
                Optional.of(academicYear(AcademicYearStatus.PLANNED));
        gradeLevelRepositoryStub.foundGradeLevel = Optional.empty();
        assertThatThrownBy(() -> service.create(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, classTeacherRequest(TEACHER_USER_ID)))
                .isInstanceOf(GradeLevelNotFoundException.class)
                .hasMessage("Grade level not found");

        gradeLevelRepositoryStub.foundGradeLevel = Optional.of(gradeLevel());
        sectionRepositoryStub.foundSection = Optional.empty();
        assertThatThrownBy(() -> service.create(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, classTeacherRequest(TEACHER_USER_ID)))
                .isInstanceOf(SectionNotFoundException.class)
                .hasMessage("Section not found");

        sectionRepositoryStub.foundSection = Optional.of(section());
        gradeSubjectRepositoryStub.foundGradeSubject = Optional.empty();
        assertThatThrownBy(() -> service.create(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, subjectTeacherRequest(TEACHER_USER_ID)))
                .isInstanceOf(GradeLevelSubjectNotFoundException.class)
                .hasMessage("Grade-subject assignment not found");
    }

    @Test
    void duplicateAssignmentsAreRejected() {
        assignmentRepositoryStub.duplicateClassTeacher = true;
        assertThatThrownBy(() -> service.create(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, classTeacherRequest(TEACHER_USER_ID)))
                .isInstanceOf(TeacherAssignmentConflictException.class)
                .hasMessage("This section already has a class teacher");

        assignmentRepositoryStub.duplicateClassTeacher = false;
        assignmentRepositoryStub.duplicateSubjectTeacher = true;
        assertThatThrownBy(() -> service.create(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, subjectTeacherRequest(TEACHER_USER_ID)))
                .isInstanceOf(TeacherAssignmentConflictException.class)
                .hasMessage("This section already has a teacher for this subject");
    }

    @Test
    void eligibleTeacherIsRequired() {
        assertIneligible(
                TeachingEligibilityReason.USER_NOT_FOUND,
                "The selected teacher was not found in this organization"
        );
        assertIneligible(
                TeachingEligibilityReason.USER_NOT_ACTIVE,
                "The selected teacher account is not active"
        );
        assertIneligible(
                TeachingEligibilityReason.TEACHER_ROLE_REQUIRED,
                "The selected user does not have the teacher role"
        );
    }

    @Test
    void inconsistentIdentityResponseIsRejected() {
        eligibilityClientStub.response = new TeachingEligibilityResponse(
                999L,
                ORGANIZATION_ID,
                "Teacher",
                true,
                TeachingEligibilityReason.ELIGIBLE
        );

        assertThatThrownBy(() -> service.create(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, classTeacherRequest(TEACHER_USER_ID)))
                .isInstanceOf(TeacherNotEligibleException.class)
                .hasMessage("Identity-service returned inconsistent teacher information");
    }

    @Test
    void identityClientFailurePreventsSave() {
        eligibilityClientStub.exception =
                new IdentityTeacherEligibilityException("down", null);

        assertThatThrownBy(() -> service.create(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, classTeacherRequest(TEACHER_USER_ID)))
                .isInstanceOf(IdentityTeacherEligibilityException.class);

        assertThat(assignmentRepositoryStub.lastSavedAssignment).isNull();
    }

    @Test
    void getListUpdateAndRemoveSucceed() {
        TeacherAssignment existing =
                assignment(ASSIGNMENT_ID, null, TEACHER_USER_ID, TeacherAssignmentType.CLASS_TEACHER, 3L);
        assignmentRepositoryStub.foundAssignment = Optional.of(existing);
        assignmentRepositoryStub.savedAssignment = existing;
        assignmentRepositoryStub.allAssignments = List.of(existing);

        assertThat(service.getById(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, ASSIGNMENT_ID).id())
                .isEqualTo(ASSIGNMENT_ID);
        assertThat(service.getAll(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID))
                .hasSize(1);

        eligibilityClientStub.response = eligible(61L);

        TeacherAssignmentResponse updateResponse = service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                ASSIGNMENT_ID,
                new UpdateTeacherAssignmentRequest(61L, 3L)
        );
        assertThat(updateResponse.teacherUserId()).isEqualTo(61L);
        assertThat(eligibilityClientStub.lastUserId).isEqualTo(61L);

        service.remove(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, ASSIGNMENT_ID);
        assertThat(assignmentRepositoryStub.deletedAssignment).isSameAs(existing);
        assertThat(assignmentRepositoryStub.flushCalls).isEqualTo(1);
    }

    @Test
    void findAssignmentIsTenantSafe() {
        assignmentRepositoryStub.foundAssignment =
                Optional.of(assignment(ASSIGNMENT_ID, null, TEACHER_USER_ID, TeacherAssignmentType.CLASS_TEACHER, 0L));

        service.getById(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, ASSIGNMENT_ID);

        assertThat(assignmentRepositoryStub.lastFindId).isEqualTo(ASSIGNMENT_ID);
        assertThat(assignmentRepositoryStub.lastFindSectionId).isEqualTo(SECTION_ID);
        assertThat(assignmentRepositoryStub.lastFindGradeLevelId).isEqualTo(GRADE_LEVEL_ID);
        assertThat(assignmentRepositoryStub.lastFindAcademicYearId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(assignmentRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
    }

    @Test
    void updateRejectsStaleVersionAndRevalidatesReplacementTeacher() {
        assignmentRepositoryStub.foundAssignment =
                Optional.of(assignment(ASSIGNMENT_ID, null, TEACHER_USER_ID, TeacherAssignmentType.CLASS_TEACHER, 3L));

        assertThatThrownBy(() -> service.update(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, ASSIGNMENT_ID, new UpdateTeacherAssignmentRequest(61L, 2L)))
                .isInstanceOf(TeacherAssignmentConflictException.class)
                .hasMessage("Teacher assignment was modified by another request");

        eligibilityClientStub.response = ineligible(
                61L,
                TeachingEligibilityReason.USER_NOT_ACTIVE
        );
        assertThatThrownBy(() -> service.update(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, ASSIGNMENT_ID, new UpdateTeacherAssignmentRequest(61L, 3L)))
                .isInstanceOf(TeacherNotEligibleException.class)
                .hasMessage("The selected teacher account is not active");
    }

    @Test
    void modificationsAreAllowedForPlannedAndActiveYears() {
        for (AcademicYearStatus status : List.of(AcademicYearStatus.PLANNED, AcademicYearStatus.ACTIVE)) {
            academicYearRepositoryStub.foundAcademicYear =
                    Optional.of(academicYear(status));
            TeacherAssignment existing =
                    assignment(ASSIGNMENT_ID, null, TEACHER_USER_ID, TeacherAssignmentType.CLASS_TEACHER, 3L);
            assignmentRepositoryStub.foundAssignment = Optional.of(existing);
            assignmentRepositoryStub.savedAssignment = existing;

            assertThat(service.create(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, classTeacherRequest(TEACHER_USER_ID)).teacherUserId())
                    .isEqualTo(TEACHER_USER_ID);
            assertThat(service.update(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, ASSIGNMENT_ID, new UpdateTeacherAssignmentRequest(TEACHER_USER_ID, 3L)).id())
                    .isEqualTo(ASSIGNMENT_ID);
            service.remove(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, ASSIGNMENT_ID);
        }
    }

    @Test
    void modificationsAreRejectedForClosedAndVoidedYears() {
        for (AcademicYearStatus status : List.of(AcademicYearStatus.CLOSED, AcademicYearStatus.VOIDED)) {
            academicYearRepositoryStub.foundAcademicYear =
                    Optional.of(academicYear(status));

            assertThatThrownBy(() -> service.create(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, classTeacherRequest(TEACHER_USER_ID)))
                    .isInstanceOf(TeacherAssignmentConflictException.class)
                    .hasMessage("Teacher assignments can only be modified for a planned or active academic year");
            assertThatThrownBy(() -> service.update(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, ASSIGNMENT_ID, new UpdateTeacherAssignmentRequest(TEACHER_USER_ID, 3L)))
                    .isInstanceOf(TeacherAssignmentConflictException.class)
                    .hasMessage("Teacher assignments can only be modified for a planned or active academic year");
            assertThatThrownBy(() -> service.remove(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, ASSIGNMENT_ID))
                    .isInstanceOf(TeacherAssignmentConflictException.class)
                    .hasMessage("Teacher assignments can only be modified for a planned or active academic year");
        }
    }

    @Test
    void missingAssignmentIsRejected() {
        assignmentRepositoryStub.foundAssignment = Optional.empty();

        assertThatThrownBy(() -> service.getById(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, ASSIGNMENT_ID))
                .isInstanceOf(TeacherAssignmentNotFoundException.class)
                .hasMessage("Teacher assignment not found");
    }

    private void assertIneligible(
            TeachingEligibilityReason reason,
            String message
    ) {
        eligibilityClientStub.response = ineligible(TEACHER_USER_ID, reason);

        assertThatThrownBy(() -> service.create(ORGANIZATION_ID, ACADEMIC_YEAR_ID, GRADE_LEVEL_ID, SECTION_ID, classTeacherRequest(TEACHER_USER_ID)))
                .isInstanceOf(TeacherNotEligibleException.class)
                .hasMessage(message);
    }

    private static CreateTeacherAssignmentRequest classTeacherRequest(Long teacherUserId) {
        return new CreateTeacherAssignmentRequest(
                null,
                teacherUserId,
                TeacherAssignmentType.CLASS_TEACHER
        );
    }

    private static CreateTeacherAssignmentRequest subjectTeacherRequest(Long teacherUserId) {
        return new CreateTeacherAssignmentRequest(
                GRADE_SUBJECT_ID,
                teacherUserId,
                TeacherAssignmentType.SUBJECT_TEACHER
        );
    }

    private static TeachingEligibilityResponse eligible(Long userId) {
        return new TeachingEligibilityResponse(
                userId,
                ORGANIZATION_ID,
                "Teacher",
                true,
                TeachingEligibilityReason.ELIGIBLE
        );
    }

    private static TeachingEligibilityResponse ineligible(
            Long userId,
            TeachingEligibilityReason reason
    ) {
        return new TeachingEligibilityResponse(
                userId,
                ORGANIZATION_ID,
                "Teacher",
                false,
                reason
        );
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

    private static Section section() {
        Section section = new Section(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                "A",
                "Section A",
                1
        );
        ReflectionTestUtils.setField(section, "id", SECTION_ID);
        return section;
    }

    private static GradeLevelSubject gradeSubject() {
        GradeLevelSubject gradeSubject = new GradeLevelSubject(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                80L,
                true,
                1
        );
        ReflectionTestUtils.setField(gradeSubject, "id", GRADE_SUBJECT_ID);
        return gradeSubject;
    }

    private static TeacherAssignment assignment(
            Long id,
            Long gradeLevelSubjectId,
            Long teacherUserId,
            TeacherAssignmentType type,
            Long version
    ) {
        TeacherAssignment assignment = new TeacherAssignment(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                gradeLevelSubjectId,
                teacherUserId,
                type
        );
        ReflectionTestUtils.setField(assignment, "id", id);
        ReflectionTestUtils.setField(assignment, "version", version);
        ReflectionTestUtils.setField(assignment, "createdAt", OffsetDateTime.parse("2026-04-01T00:00:00Z"));
        ReflectionTestUtils.setField(assignment, "updatedAt", OffsetDateTime.parse("2026-04-01T00:00:00Z"));
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

    private static class SectionRepositoryStub {
        private Optional<Section> foundSection = Optional.empty();
        private long lastFindId;
        private long lastFindGradeLevelId;
        private long lastFindAcademicYearId;
        private long lastFindOrganizationId;

        private SectionRepository repository() {
            return (SectionRepository) Proxy.newProxyInstance(
                    SectionRepository.class.getClassLoader(),
                    new Class<?>[]{SectionRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId" -> {
                            lastFindId = (Long) args[0];
                            lastFindGradeLevelId = (Long) args[1];
                            lastFindAcademicYearId = (Long) args[2];
                            lastFindOrganizationId = (Long) args[3];
                            yield foundSection;
                        }
                        case "toString" -> "SectionRepositoryStub";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static class GradeSubjectRepositoryStub {
        private Optional<GradeLevelSubject> foundGradeSubject = Optional.empty();
        private long lastFindId;
        private long lastFindGradeLevelId;
        private long lastFindAcademicYearId;
        private long lastFindOrganizationId;

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
                            yield foundGradeSubject;
                        }
                        case "toString" -> "GradeLevelSubjectRepositoryStub";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static class AssignmentRepositoryStub {
        private Optional<TeacherAssignment> foundAssignment = Optional.empty();
        private List<TeacherAssignment> allAssignments = List.of();
        private TeacherAssignment savedAssignment;
        private TeacherAssignment lastSavedAssignment;
        private TeacherAssignment deletedAssignment;
        private long lastFindId;
        private long lastFindSectionId;
        private long lastFindGradeLevelId;
        private long lastFindAcademicYearId;
        private long lastFindOrganizationId;
        private boolean duplicateClassTeacher;
        private boolean duplicateSubjectTeacher;
        private int flushCalls;

        private TeacherAssignmentRepository repository() {
            return (TeacherAssignmentRepository) Proxy.newProxyInstance(
                    TeacherAssignmentRepository.class.getClassLoader(),
                    new Class<?>[]{TeacherAssignmentRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByIdAndSectionIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId" -> {
                            lastFindId = (Long) args[0];
                            lastFindSectionId = (Long) args[1];
                            lastFindGradeLevelId = (Long) args[2];
                            lastFindAcademicYearId = (Long) args[3];
                            lastFindOrganizationId = (Long) args[4];
                            yield foundAssignment;
                        }
                        case "findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdOrderByAssignmentTypeAscIdAsc" -> allAssignments;
                        case "existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndAssignmentType" -> duplicateClassTeacher;
                        case "existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndGradeLevelSubjectId" -> duplicateSubjectTeacher;
                        case "saveAndFlush" -> {
                            lastSavedAssignment = (TeacherAssignment) args[0];
                            yield savedAssignment != null ? savedAssignment : lastSavedAssignment;
                        }
                        case "delete" -> {
                            deletedAssignment = (TeacherAssignment) args[0];
                            yield null;
                        }
                        case "flush" -> {
                            flushCalls++;
                            yield null;
                        }
                        case "toString" -> "TeacherAssignmentRepositoryStub";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static class EligibilityClientStub
            implements IdentityTeacherEligibilityClient {
        private TeachingEligibilityResponse response;
        private RuntimeException exception;
        private long lastOrganizationId;
        private long lastUserId;

        @Override
        public TeachingEligibilityResponse check(
                long organizationId,
                long userId
        ) {
            lastOrganizationId = organizationId;
            lastUserId = userId;
            if (exception != null) {
                throw exception;
            }
            return response;
        }

        @Override
        public com.dawnrise.academic.teacherassignment.integration.identity.BatchTeachingEligibilityResponse checkBatch(
                long organizationId,
                java.util.List<Long> userIds
        ) {
            throw new UnsupportedOperationException(
                    "Batch eligibility is not used by these tests"
            );
        }
    }
}
