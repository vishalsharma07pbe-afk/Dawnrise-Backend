package com.dawnrise.academic.subject.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.subject.dto.CreateSubjectRequest;
import com.dawnrise.academic.subject.dto.SubjectResponse;
import com.dawnrise.academic.subject.dto.UpdateSubjectRequest;
import com.dawnrise.academic.subject.entity.Subject;
import com.dawnrise.academic.subject.exception.SubjectConflictException;
import com.dawnrise.academic.subject.exception.SubjectNotFoundException;
import com.dawnrise.academic.subject.mapper.SubjectMapper;
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

class SubjectServiceImplTest {

    private static final long ORGANIZATION_ID = 10L;
    private static final long ACADEMIC_YEAR_ID = 20L;
    private static final long SUBJECT_ID = 30L;

    private AcademicYearRepositoryStub academicYearRepositoryStub;
    private SubjectRepositoryStub subjectRepositoryStub;
    private SubjectServiceImpl service;

    @BeforeEach
    void setUp() {
        academicYearRepositoryStub = new AcademicYearRepositoryStub();
        subjectRepositoryStub = new SubjectRepositoryStub();
        academicYearRepositoryStub.foundAcademicYear =
                Optional.of(academicYear(AcademicYearStatus.PLANNED));
        service = new SubjectServiceImpl(
                academicYearRepositoryStub.repository(),
                subjectRepositoryStub.repository(),
                new SubjectMapper()
        );
    }

    @Test
    void createSucceeds() {
        subjectRepositoryStub.savedSubject =
                subject(SUBJECT_ID, "MATH", "Mathematics", "Core", 0L);

        SubjectResponse response = service.create(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                new CreateSubjectRequest(" math ", " Mathematics ", " Core ")
        );

        assertThat(response.id()).isEqualTo(SUBJECT_ID);
        assertThat(response.code()).isEqualTo("MATH");
        assertThat(response.name()).isEqualTo("Mathematics");
        assertThat(response.description()).isEqualTo("Core");
        assertThat(subjectRepositoryStub.lastSavedSubject.getCode()).isEqualTo("MATH");
        assertThat(academicYearRepositoryStub.lastFindId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(academicYearRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
    }

    @Test
    void getByIdUsesTenantSafeLookups() {
        subjectRepositoryStub.foundSubject =
                Optional.of(subject(SUBJECT_ID, "MATH", "Mathematics", null, 0L));

        SubjectResponse response = service.getById(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                SUBJECT_ID
        );

        assertThat(response.id()).isEqualTo(SUBJECT_ID);
        assertThat(academicYearRepositoryStub.lastFindId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(academicYearRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
        assertThat(subjectRepositoryStub.lastFindId).isEqualTo(SUBJECT_ID);
        assertThat(subjectRepositoryStub.lastFindAcademicYearId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(subjectRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
    }

    @Test
    void getAllMapsResultsInRepositoryOrder() {
        subjectRepositoryStub.allSubjects = List.of(
                subject(1L, "ART", "Art", null, 0L),
                subject(2L, "MATH", "Mathematics", null, 0L)
        );

        List<SubjectResponse> responses = service.getAll(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID
        );

        assertThat(responses).extracting(SubjectResponse::code)
                .containsExactly("ART", "MATH");
        assertThat(subjectRepositoryStub.lastListOrganizationId).isEqualTo(ORGANIZATION_ID);
        assertThat(subjectRepositoryStub.lastListAcademicYearId).isEqualTo(ACADEMIC_YEAR_ID);
    }

    @Test
    void updateSucceedsAndExcludesCurrentIdFromDuplicateChecks() {
        Subject existing = subject(SUBJECT_ID, "MATH", "Mathematics", null, 3L);
        subjectRepositoryStub.foundSubject = Optional.of(existing);
        subjectRepositoryStub.savedSubject = existing;

        SubjectResponse response = service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                SUBJECT_ID,
                new UpdateSubjectRequest(" sci ", " Science ", " Updated ", 3L)
        );

        assertThat(response.code()).isEqualTo("SCI");
        assertThat(response.name()).isEqualTo("Science");
        assertThat(response.description()).isEqualTo("Updated");
        assertThat(subjectRepositoryStub.lastDuplicateExclusionId).isEqualTo(SUBJECT_ID);
    }

    @Test
    void duplicateCodeAndCaseInsensitiveNameAreRejectedOnCreate() {
        subjectRepositoryStub.duplicateCode = true;

        assertThatThrownBy(() -> service.create(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                createRequest()
        )).isInstanceOf(SubjectConflictException.class)
                .hasMessage("A subject with this code already exists");

        subjectRepositoryStub.duplicateCode = false;
        subjectRepositoryStub.duplicateName = true;

        assertThatThrownBy(() -> service.create(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                new CreateSubjectRequest("SCI", "mathematics", null)
        )).isInstanceOf(SubjectConflictException.class)
                .hasMessage("A subject with this name already exists");
    }

    @Test
    void duplicateCodeAndNameAreRejectedOnUpdate() {
        subjectRepositoryStub.foundSubject =
                Optional.of(subject(SUBJECT_ID, "MATH", "Mathematics", null, 3L));
        subjectRepositoryStub.duplicateCode = true;

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                SUBJECT_ID,
                updateRequest(3L)
        )).isInstanceOf(SubjectConflictException.class)
                .hasMessage("A subject with this code already exists");

        subjectRepositoryStub.duplicateCode = false;
        subjectRepositoryStub.duplicateName = true;

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                SUBJECT_ID,
                updateRequest(3L)
        )).isInstanceOf(SubjectConflictException.class)
                .hasMessage("A subject with this name already exists");
    }

    @Test
    void updateRejectsStaleVersion() {
        subjectRepositoryStub.foundSubject =
                Optional.of(subject(SUBJECT_ID, "MATH", "Mathematics", null, 3L));

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                SUBJECT_ID,
                updateRequest(2L)
        )).isInstanceOf(SubjectConflictException.class)
                .hasMessage("Subject was modified by another request");
    }

    @Test
    void missingAcademicYearOrSubjectIsRejected() {
        academicYearRepositoryStub.foundAcademicYear = Optional.empty();

        assertThatThrownBy(() -> service.getById(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                SUBJECT_ID
        )).isInstanceOf(AcademicYearNotFoundException.class)
                .hasMessage("Academic year not found");

        academicYearRepositoryStub.foundAcademicYear =
                Optional.of(academicYear(AcademicYearStatus.PLANNED));
        subjectRepositoryStub.foundSubject = Optional.empty();

        assertThatThrownBy(() -> service.getById(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                SUBJECT_ID
        )).isInstanceOf(SubjectNotFoundException.class)
                .hasMessage("Subject not found");
    }

    @Test
    void createAndUpdateAreAllowedForPlannedAndActiveYears() {
        for (AcademicYearStatus status : List.of(
                AcademicYearStatus.PLANNED,
                AcademicYearStatus.ACTIVE
        )) {
            academicYearRepositoryStub.foundAcademicYear =
                    Optional.of(academicYear(status));
            subjectRepositoryStub.savedSubject =
                    subject(SUBJECT_ID, "MATH", "Mathematics", null, 0L);

            assertThat(service.create(
                    ORGANIZATION_ID,
                    ACADEMIC_YEAR_ID,
                    createRequest()
            ).code()).isEqualTo("MATH");

            Subject existing = subject(SUBJECT_ID, "MATH", "Mathematics", null, 3L);
            subjectRepositoryStub.foundSubject = Optional.of(existing);
            subjectRepositoryStub.savedSubject = existing;

            assertThat(service.update(
                    ORGANIZATION_ID,
                    ACADEMIC_YEAR_ID,
                    SUBJECT_ID,
                    updateRequest(3L)
            ).code()).isEqualTo("MATH");
        }
    }

    @Test
    void createAndUpdateAreRejectedForClosedAndVoidedYears() {
        for (AcademicYearStatus status : List.of(
                AcademicYearStatus.CLOSED,
                AcademicYearStatus.VOIDED
        )) {
            academicYearRepositoryStub.foundAcademicYear =
                    Optional.of(academicYear(status));

            assertThatThrownBy(() -> service.create(
                    ORGANIZATION_ID,
                    ACADEMIC_YEAR_ID,
                    createRequest()
            )).isInstanceOf(SubjectConflictException.class)
                    .hasMessage("Subjects can only be modified for a planned or active academic year");

            assertThatThrownBy(() -> service.update(
                    ORGANIZATION_ID,
                    ACADEMIC_YEAR_ID,
                    SUBJECT_ID,
                    updateRequest(3L)
            )).isInstanceOf(SubjectConflictException.class)
                    .hasMessage("Subjects can only be modified for a planned or active academic year");
        }
    }

    @Test
    void readIsAllowedForHistoricalYears() {
        for (AcademicYearStatus status : List.of(
                AcademicYearStatus.CLOSED,
                AcademicYearStatus.VOIDED
        )) {
            academicYearRepositoryStub.foundAcademicYear =
                    Optional.of(academicYear(status));
            subjectRepositoryStub.foundSubject =
                    Optional.of(subject(SUBJECT_ID, "MATH", "Mathematics", null, 0L));
            subjectRepositoryStub.allSubjects =
                    List.of(subject(SUBJECT_ID, "MATH", "Mathematics", null, 0L));

            assertThat(service.getById(
                    ORGANIZATION_ID,
                    ACADEMIC_YEAR_ID,
                    SUBJECT_ID
            ).code()).isEqualTo("MATH");
            assertThat(service.getAll(ORGANIZATION_ID, ACADEMIC_YEAR_ID))
                    .hasSize(1);
        }
    }

    private static CreateSubjectRequest createRequest() {
        return new CreateSubjectRequest("MATH", "Mathematics", null);
    }

    private static UpdateSubjectRequest updateRequest(Long version) {
        return new UpdateSubjectRequest("MATH", "Mathematics", null, version);
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

    private static Subject subject(
            Long id,
            String code,
            String name,
            String description,
            Long version
    ) {
        Subject subject = new Subject(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                code,
                name,
                description
        );
        ReflectionTestUtils.setField(subject, "id", id);
        ReflectionTestUtils.setField(subject, "version", version);
        ReflectionTestUtils.setField(
                subject,
                "createdAt",
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
        ReflectionTestUtils.setField(
                subject,
                "updatedAt",
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
        return subject;
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

    private static class SubjectRepositoryStub {

        private Optional<Subject> foundSubject = Optional.empty();
        private List<Subject> allSubjects = List.of();
        private Subject savedSubject;
        private Subject lastSavedSubject;
        private long lastFindId;
        private long lastFindAcademicYearId;
        private long lastFindOrganizationId;
        private long lastListOrganizationId;
        private long lastListAcademicYearId;
        private long lastDuplicateExclusionId;
        private boolean duplicateCode;
        private boolean duplicateName;

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
                        case "findAllByOrganizationIdAndAcademicYearIdOrderByNameAsc" -> {
                            lastListOrganizationId = (Long) args[0];
                            lastListAcademicYearId = (Long) args[1];
                            yield allSubjects;
                        }
                        case "existsByAcademicYearIdAndCodeIgnoreCase" -> duplicateCode;
                        case "existsByAcademicYearIdAndNameIgnoreCase" -> duplicateName;
                        case "existsByAcademicYearIdAndCodeIgnoreCaseAndIdNot" -> {
                            lastDuplicateExclusionId = (Long) args[2];
                            yield duplicateCode;
                        }
                        case "existsByAcademicYearIdAndNameIgnoreCaseAndIdNot" -> {
                            lastDuplicateExclusionId = (Long) args[2];
                            yield duplicateName;
                        }
                        case "saveAndFlush" -> {
                            lastSavedSubject = (Subject) args[0];
                            yield savedSubject != null
                                    ? savedSubject
                                    : lastSavedSubject;
                        }
                        case "toString" -> "SubjectRepositoryStub";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
