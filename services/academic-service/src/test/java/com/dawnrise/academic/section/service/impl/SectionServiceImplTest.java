package com.dawnrise.academic.section.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.exception.GradeLevelNotFoundException;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.section.dto.CreateSectionRequest;
import com.dawnrise.academic.section.dto.SectionResponse;
import com.dawnrise.academic.section.dto.UpdateSectionRequest;
import com.dawnrise.academic.section.dto.BulkCreateSectionsRequest;
import com.dawnrise.academic.section.dto.ApplySectionStructureRequest;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.section.exception.SectionConflictException;
import com.dawnrise.academic.section.exception.SectionNotFoundException;
import com.dawnrise.academic.section.mapper.SectionMapper;
import com.dawnrise.academic.section.repository.SectionRepository;
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

class SectionServiceImplTest {

    private static final long ORGANIZATION_ID = 10L;
    private static final long ACADEMIC_YEAR_ID = 20L;
    private static final long GRADE_LEVEL_ID = 30L;
    private static final long SECTION_ID = 40L;

    private AcademicYearRepositoryStub academicYearRepositoryStub;
    private GradeLevelRepositoryStub gradeLevelRepositoryStub;
    private SectionRepositoryStub sectionRepositoryStub;
    private SectionServiceImpl service;

    @BeforeEach
    void setUp() {
        academicYearRepositoryStub = new AcademicYearRepositoryStub();
        gradeLevelRepositoryStub = new GradeLevelRepositoryStub();
        sectionRepositoryStub = new SectionRepositoryStub();
        academicYearRepositoryStub.foundAcademicYear =
                Optional.of(academicYear(AcademicYearStatus.PLANNED));
        gradeLevelRepositoryStub.foundGradeLevel = Optional.of(gradeLevel());
        service = new SectionServiceImpl(
                academicYearRepositoryStub.repository(),
                gradeLevelRepositoryStub.repository(),
                sectionRepositoryStub.repository(),
                new SectionMapper()
        );
    }

    @Test
    void createSucceeds() {
        sectionRepositoryStub.savedSection =
                section(SECTION_ID, "A", "Section A", 1, 0L);

        SectionResponse response = service.create(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                new CreateSectionRequest(" a ", " Section A ", 1)
        );

        assertThat(response.id()).isEqualTo(SECTION_ID);
        assertThat(response.code()).isEqualTo("A");
        assertThat(response.name()).isEqualTo("Section A");
        assertThat(sectionRepositoryStub.lastSavedSection.getCode()).isEqualTo("A");
        assertThat(academicYearRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
        assertThat(gradeLevelRepositoryStub.lastFindAcademicYearId).isEqualTo(ACADEMIC_YEAR_ID);
    }

    @Test
    void getByIdUsesTenantSafeLookups() {
        sectionRepositoryStub.foundSection =
                Optional.of(section(SECTION_ID, "A", "Section A", 1, 0L));

        SectionResponse response = service.getById(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID
        );

        assertThat(response.id()).isEqualTo(SECTION_ID);
        assertThat(academicYearRepositoryStub.lastFindId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(academicYearRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
        assertThat(gradeLevelRepositoryStub.lastFindId).isEqualTo(GRADE_LEVEL_ID);
        assertThat(gradeLevelRepositoryStub.lastFindAcademicYearId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(gradeLevelRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
        assertThat(sectionRepositoryStub.lastFindId).isEqualTo(SECTION_ID);
        assertThat(sectionRepositoryStub.lastFindGradeLevelId).isEqualTo(GRADE_LEVEL_ID);
        assertThat(sectionRepositoryStub.lastFindAcademicYearId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(sectionRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
    }

    @Test
    void getByIdRejectsMissingParentsAndSection() {
        academicYearRepositoryStub.foundAcademicYear = Optional.empty();

        assertThatThrownBy(() -> service.getById(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID
        )).isInstanceOf(AcademicYearNotFoundException.class)
                .hasMessage("Academic year not found");

        academicYearRepositoryStub.foundAcademicYear =
                Optional.of(academicYear(AcademicYearStatus.PLANNED));
        gradeLevelRepositoryStub.foundGradeLevel = Optional.empty();

        assertThatThrownBy(() -> service.getById(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID
        )).isInstanceOf(GradeLevelNotFoundException.class)
                .hasMessage("Grade level not found");

        gradeLevelRepositoryStub.foundGradeLevel = Optional.of(gradeLevel());
        sectionRepositoryStub.foundSection = Optional.empty();

        assertThatThrownBy(() -> service.getById(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID
        )).isInstanceOf(SectionNotFoundException.class)
                .hasMessage("Section not found");
    }

    @Test
    void getAllMapsResultsInRepositoryOrder() {
        sectionRepositoryStub.allSections = List.of(
                section(1L, "A", "Section A", 1, 0L),
                section(2L, "B", "Section B", 2, 0L)
        );

        List<SectionResponse> responses = service.getAll(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID
        );

        assertThat(responses).extracting(SectionResponse::code)
                .containsExactly("A", "B");
        assertThat(sectionRepositoryStub.lastListOrganizationId).isEqualTo(ORGANIZATION_ID);
        assertThat(sectionRepositoryStub.lastListAcademicYearId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(sectionRepositoryStub.lastListGradeLevelId).isEqualTo(GRADE_LEVEL_ID);
    }

    @Test
    void updateSucceedsAndExcludesCurrentIdFromDuplicateChecks() {
        Section existing = section(SECTION_ID, "A", "Section A", 1, 3L);
        sectionRepositoryStub.foundSection = Optional.of(existing);
        sectionRepositoryStub.savedSection = existing;

        SectionResponse response = service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                new UpdateSectionRequest(" b ", " Section B ", 2, 3L)
        );

        assertThat(response.code()).isEqualTo("B");
        assertThat(response.name()).isEqualTo("Section B");
        assertThat(response.displayOrder()).isEqualTo(2);
        assertThat(sectionRepositoryStub.lastDuplicateExclusionId).isEqualTo(SECTION_ID);
    }

    @Test
    void duplicateCodeNameAndDisplayOrderAreRejectedOnCreate() {
        sectionRepositoryStub.duplicateCode = true;

        assertThatThrownBy(() -> service.create(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                createRequest()
        )).isInstanceOf(SectionConflictException.class)
                .hasMessage("A section with this code already exists");

        sectionRepositoryStub.duplicateCode = false;
        sectionRepositoryStub.duplicateName = true;

        assertThatThrownBy(() -> service.create(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                createRequest()
        )).isInstanceOf(SectionConflictException.class)
                .hasMessage("A section with this name already exists");

        sectionRepositoryStub.duplicateName = false;
        sectionRepositoryStub.duplicateDisplayOrder = true;

        assertThatThrownBy(() -> service.create(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                createRequest()
        )).isInstanceOf(SectionConflictException.class)
                .hasMessage("A section with this display order already exists");
    }

    @Test
    void duplicateCodeNameAndDisplayOrderAreRejectedOnUpdate() {
        sectionRepositoryStub.foundSection =
                Optional.of(section(SECTION_ID, "A", "Section A", 1, 3L));
        sectionRepositoryStub.duplicateCode = true;

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                updateRequest(3L)
        )).isInstanceOf(SectionConflictException.class)
                .hasMessage("A section with this code already exists");

        sectionRepositoryStub.duplicateCode = false;
        sectionRepositoryStub.duplicateName = true;

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                updateRequest(3L)
        )).isInstanceOf(SectionConflictException.class)
                .hasMessage("A section with this name already exists");

        sectionRepositoryStub.duplicateName = false;
        sectionRepositoryStub.duplicateDisplayOrder = true;

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                updateRequest(3L)
        )).isInstanceOf(SectionConflictException.class)
                .hasMessage("A section with this display order already exists");
    }

    @Test
    void updateRejectsStaleVersion() {
        sectionRepositoryStub.foundSection =
                Optional.of(section(SECTION_ID, "A", "Section A", 1, 3L));

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                SECTION_ID,
                updateRequest(2L)
        )).isInstanceOf(SectionConflictException.class)
                .hasMessage("Section was modified by another request");
    }

    @Test
    void createAndUpdateAreAllowedForPlannedAndActiveYears() {
        for (AcademicYearStatus status : List.of(
                AcademicYearStatus.PLANNED,
                AcademicYearStatus.ACTIVE
        )) {
            academicYearRepositoryStub.foundAcademicYear =
                    Optional.of(academicYear(status));
            sectionRepositoryStub.savedSection =
                    section(SECTION_ID, "A", "Section A", 1, 0L);

            SectionResponse createResponse = service.create(
                    ORGANIZATION_ID,
                    ACADEMIC_YEAR_ID,
                    GRADE_LEVEL_ID,
                    createRequest()
            );

            assertThat(createResponse.code()).isEqualTo("A");

            Section existing = section(SECTION_ID, "A", "Section A", 1, 3L);
            sectionRepositoryStub.foundSection = Optional.of(existing);
            sectionRepositoryStub.savedSection = existing;

            SectionResponse updateResponse = service.update(
                    ORGANIZATION_ID,
                    ACADEMIC_YEAR_ID,
                    GRADE_LEVEL_ID,
                    SECTION_ID,
                    updateRequest(3L)
            );

            assertThat(updateResponse.code()).isEqualTo("A");
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
                    GRADE_LEVEL_ID,
                    createRequest()
            )).isInstanceOf(SectionConflictException.class)
                    .hasMessage("Sections can only be modified for a planned or active academic year");

            assertThatThrownBy(() -> service.update(
                    ORGANIZATION_ID,
                    ACADEMIC_YEAR_ID,
                    GRADE_LEVEL_ID,
                    SECTION_ID,
                    updateRequest(3L)
            )).isInstanceOf(SectionConflictException.class)
                    .hasMessage("Sections can only be modified for a planned or active academic year");
        }
    }

    @Test
    void createBulkSucceeds() {
        sectionRepositoryStub.savedSections = List.of(
                section(1L, "A", "Section A", 1, 0L),
                section(2L, "B", "Section B", 2, 0L)
        );

        List<SectionResponse> responses = service.createBulk(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                new BulkCreateSectionsRequest(List.of(
                        new CreateSectionRequest(" a ", " Section A ", 1),
                        new CreateSectionRequest(" b ", " Section B ", 2)
                ))
        );

        assertThat(responses).extracting(SectionResponse::code)
                .containsExactly("A", "B");
        assertThat(sectionRepositoryStub.lastSavedSections)
                .extracting(Section::getGradeLevelId)
                .containsExactly(GRADE_LEVEL_ID, GRADE_LEVEL_ID);
        assertThat(sectionRepositoryStub.flushCalls).isEqualTo(1);
    }

    @Test
    void createBulkRejectsDuplicatesInsideRequest() {
        assertThatThrownBy(() -> service.createBulk(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                new BulkCreateSectionsRequest(List.of(
                        new CreateSectionRequest(" a ", "Section A", 1),
                        new CreateSectionRequest("A", "Section B", 2)
                ))
        )).isInstanceOf(SectionConflictException.class)
                .hasMessage("The bulk request contains a duplicate section code");

        assertThatThrownBy(() -> service.createBulk(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                new BulkCreateSectionsRequest(List.of(
                        new CreateSectionRequest("A", " Section A ", 1),
                        new CreateSectionRequest("B", "section a", 2)
                ))
        )).isInstanceOf(SectionConflictException.class)
                .hasMessage("The bulk request contains a duplicate section name");

        assertThatThrownBy(() -> service.createBulk(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                new BulkCreateSectionsRequest(List.of(
                        new CreateSectionRequest("A", "Section A", 1),
                        new CreateSectionRequest("B", "Section B", 1)
                ))
        )).isInstanceOf(SectionConflictException.class)
                .hasMessage("The bulk request contains a duplicate display order");

        assertThat(sectionRepositoryStub.saveAllCalls).isZero();
    }

    @Test
    void createBulkRejectsDatabaseConflicts() {
        sectionRepositoryStub.duplicateCode = true;

        assertThatThrownBy(() -> service.createBulk(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                bulkRequest()
        )).isInstanceOf(SectionConflictException.class)
                .hasMessage("A section with code A already exists");

        sectionRepositoryStub.duplicateCode = false;
        sectionRepositoryStub.duplicateName = true;

        assertThatThrownBy(() -> service.createBulk(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                bulkRequest()
        )).isInstanceOf(SectionConflictException.class)
                .hasMessage("A section with name Section A already exists");

        sectionRepositoryStub.duplicateName = false;
        sectionRepositoryStub.duplicateDisplayOrder = true;

        assertThatThrownBy(() -> service.createBulk(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                bulkRequest()
        )).isInstanceOf(SectionConflictException.class)
                .hasMessage("Display order 1 already exists");

        assertThat(sectionRepositoryStub.saveAllCalls).isZero();
    }

    @Test
    void createBulkUsesTenantSafeParentLookup() {
        sectionRepositoryStub.savedSections =
                List.of(section(1L, "A", "Section A", 1, 0L));

        service.createBulk(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                bulkRequest()
        );

        assertThat(academicYearRepositoryStub.lastFindId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(academicYearRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
        assertThat(gradeLevelRepositoryStub.lastFindId).isEqualTo(GRADE_LEVEL_ID);
        assertThat(gradeLevelRepositoryStub.lastFindAcademicYearId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(gradeLevelRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
    }

    @Test
    void createBulkRejectsClosedAndVoidedYears() {
        for (AcademicYearStatus status : List.of(
                AcademicYearStatus.CLOSED,
                AcademicYearStatus.VOIDED
        )) {
            academicYearRepositoryStub.foundAcademicYear =
                    Optional.of(academicYear(status));

            assertThatThrownBy(() -> service.createBulk(
                    ORGANIZATION_ID,
                    ACADEMIC_YEAR_ID,
                    GRADE_LEVEL_ID,
                    bulkRequest()
            )).isInstanceOf(SectionConflictException.class)
                    .hasMessage("Sections can only be modified for a planned or active academic year");
        }

        assertThat(sectionRepositoryStub.saveAllCalls).isZero();
    }

    @Test
    void applyStructureSucceedsForMultipleGradeLevels() {
        sectionRepositoryStub.assignIdsOnSaveAll = true;

        List<SectionResponse> responses = service.applyStructure(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                new ApplySectionStructureRequest(
                        List.of(30L, 31L),
                        List.of(
                                new CreateSectionRequest("A", "Section A", 1),
                                new CreateSectionRequest("B", "Section B", 2)
                        )
                )
        );

        assertThat(responses).extracting(SectionResponse::gradeLevelId)
                .containsExactly(30L, 30L, 31L, 31L);
        assertThat(responses).extracting(SectionResponse::code)
                .containsExactly("A", "B", "A", "B");
    }

    @Test
    void applyStructureRejectsDuplicateGradeLevelIds() {
        assertThatThrownBy(() -> service.applyStructure(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                new ApplySectionStructureRequest(
                        List.of(30L, 30L),
                        List.of(new CreateSectionRequest("A", "Section A", 1))
                )
        )).isInstanceOf(SectionConflictException.class)
                .hasMessage("The request contains a duplicate grade level ID");

        assertThat(sectionRepositoryStub.saveAllCalls).isZero();
    }

    @Test
    void applyStructureRejectsGradeLevelFromAnotherOrganizationOrYear() {
        gradeLevelRepositoryStub.missingGradeLevelIds.add(31L);

        assertThatThrownBy(() -> service.applyStructure(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                new ApplySectionStructureRequest(
                        List.of(30L, 31L),
                        List.of(new CreateSectionRequest("A", "Section A", 1))
                )
        )).isInstanceOf(GradeLevelNotFoundException.class)
                .hasMessage("Grade level not found");

        assertThat(sectionRepositoryStub.saveAllCalls).isZero();
    }

    @Test
    void applyStructureRejectsIfOneGradeConflictsBeforeCompletingOperation() {
        sectionRepositoryStub.conflictingGradeLevelIds.add(31L);

        assertThatThrownBy(() -> service.applyStructure(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                new ApplySectionStructureRequest(
                        List.of(30L, 31L),
                        List.of(new CreateSectionRequest("A", "Section A", 1))
                )
        )).isInstanceOf(SectionConflictException.class)
                .hasMessage("A section with code A already exists");

        assertThat(sectionRepositoryStub.savedGradeLevelIds)
                .containsExactly(30L);
    }

    private static CreateSectionRequest createRequest() {
        return new CreateSectionRequest("A", "Section A", 1);
    }

    private static BulkCreateSectionsRequest bulkRequest() {
        return new BulkCreateSectionsRequest(List.of(createRequest()));
    }

    private static UpdateSectionRequest updateRequest(Long version) {
        return new UpdateSectionRequest("A", "Section A", 1, version);
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

    private static Section section(
            Long id,
            String code,
            String name,
            Integer displayOrder,
            Long version
    ) {
        Section section = new Section(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                code,
                name,
                displayOrder
        );
        ReflectionTestUtils.setField(section, "id", id);
        ReflectionTestUtils.setField(section, "version", version);
        ReflectionTestUtils.setField(
                section,
                "createdAt",
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
        ReflectionTestUtils.setField(
                section,
                "updatedAt",
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
        return section;
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
        private final java.util.Set<Long> missingGradeLevelIds =
                new java.util.HashSet<>();
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
                            yield missingGradeLevelIds.contains(lastFindId)
                                    ? Optional.empty()
                                    : foundGradeLevel;
                        }
                        case "toString" -> "GradeLevelRepositoryStub";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static class SectionRepositoryStub {

        private Optional<Section> foundSection = Optional.empty();
        private List<Section> allSections = List.of();
        private Section savedSection;
        private List<Section> savedSections = List.of();
        private Section lastSavedSection;
        private List<Section> lastSavedSections = List.of();
        private final List<Long> savedGradeLevelIds = new java.util.ArrayList<>();
        private long lastFindId;
        private long lastFindGradeLevelId;
        private long lastFindAcademicYearId;
        private long lastFindOrganizationId;
        private long lastListOrganizationId;
        private long lastListAcademicYearId;
        private long lastListGradeLevelId;
        private long lastDuplicateExclusionId;
        private boolean duplicateCode;
        private boolean duplicateName;
        private boolean duplicateDisplayOrder;
        private boolean assignIdsOnSaveAll;
        private final java.util.Set<Long> conflictingGradeLevelIds =
                new java.util.HashSet<>();
        private int saveAllCalls;
        private int flushCalls;

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
                        case "findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdOrderByDisplayOrderAsc" -> {
                            lastListOrganizationId = (Long) args[0];
                            lastListAcademicYearId = (Long) args[1];
                            lastListGradeLevelId = (Long) args[2];
                            yield allSections;
                        }
                        case "existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndCodeIgnoreCase" ->
                                duplicateCode
                                        || conflictingGradeLevelIds.contains((Long) args[2]);
                        case "existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndNameIgnoreCase" -> duplicateName;
                        case "existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndDisplayOrder" -> duplicateDisplayOrder;
                        case "existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndCodeIgnoreCaseAndIdNot" -> {
                            lastDuplicateExclusionId = (Long) args[4];
                            yield duplicateCode;
                        }
                        case "existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndNameIgnoreCaseAndIdNot" -> {
                            lastDuplicateExclusionId = (Long) args[4];
                            yield duplicateName;
                        }
                        case "existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndDisplayOrderAndIdNot" -> {
                            lastDuplicateExclusionId = (Long) args[4];
                            yield duplicateDisplayOrder;
                        }
                        case "saveAndFlush" -> {
                            lastSavedSection = (Section) args[0];
                            yield savedSection != null
                                    ? savedSection
                                    : lastSavedSection;
                        }
                        case "saveAll" -> {
                            saveAllCalls++;
                            java.util.ArrayList<Section> items =
                                    new java.util.ArrayList<>();
                            for (Section section : (Iterable<Section>) args[0]) {
                                items.add(section);
                                savedGradeLevelIds.add(section.getGradeLevelId());
                            }
                            if (assignIdsOnSaveAll) {
                                long nextId = saveAllCalls * 100L;
                                for (Section section : items) {
                                    ReflectionTestUtils.setField(section, "id", nextId++);
                                    ReflectionTestUtils.setField(section, "version", 0L);
                                    ReflectionTestUtils.setField(
                                            section,
                                            "createdAt",
                                            OffsetDateTime.parse("2026-04-01T00:00:00Z")
                                    );
                                    ReflectionTestUtils.setField(
                                            section,
                                            "updatedAt",
                                            OffsetDateTime.parse("2026-04-01T00:00:00Z")
                                    );
                                }
                            }
                            lastSavedSections = List.copyOf(items);
                            yield savedSections.isEmpty()
                                    ? lastSavedSections
                                    : savedSections;
                        }
                        case "flush" -> {
                            flushCalls++;
                            yield null;
                        }
                        case "toString" -> "SectionRepositoryStub";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
