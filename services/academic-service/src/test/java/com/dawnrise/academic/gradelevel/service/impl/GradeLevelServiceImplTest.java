package com.dawnrise.academic.gradelevel.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.dto.CreateGradeLevelRequest;
import com.dawnrise.academic.gradelevel.dto.GradeLevelResponse;
import com.dawnrise.academic.gradelevel.dto.UpdateGradeLevelRequest;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.exception.GradeLevelConflictException;
import com.dawnrise.academic.gradelevel.mapper.GradeLevelMapper;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
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

class GradeLevelServiceImplTest {

    private static final long ORGANIZATION_ID = 10L;
    private static final long ACADEMIC_YEAR_ID = 20L;
    private static final long GRADE_LEVEL_ID = 30L;

    private GradeLevelRepositoryStub gradeLevelRepositoryStub;
    private AcademicYearRepositoryStub academicYearRepositoryStub;
    private GradeLevelServiceImpl service;

    @BeforeEach
    void setUp() {
        gradeLevelRepositoryStub = new GradeLevelRepositoryStub();
        academicYearRepositoryStub = new AcademicYearRepositoryStub();
        academicYearRepositoryStub.foundAcademicYear =
                Optional.of(academicYear(AcademicYearStatus.PLANNED));
        service = new GradeLevelServiceImpl(
                gradeLevelRepositoryStub.repository(),
                academicYearRepositoryStub.repository(),
                new GradeLevelMapper()
        );
    }

    @Test
    void createSucceeds() {
        gradeLevelRepositoryStub.savedGradeLevel =
                gradeLevel(GRADE_LEVEL_ID, "G1", "Grade 1", 1, 0L);

        GradeLevelResponse response = service.create(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                new CreateGradeLevelRequest(" g1 ", " Grade 1 ", 1)
        );

        assertThat(response.id()).isEqualTo(GRADE_LEVEL_ID);
        assertThat(response.code()).isEqualTo("G1");
        assertThat(gradeLevelRepositoryStub.lastSavedGradeLevel.getCode()).isEqualTo("G1");
        assertThat(academicYearRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
    }

    @Test
    void createRejectsDuplicateCodeNameAndDisplayOrder() {
        gradeLevelRepositoryStub.duplicateCode = true;

        assertThatThrownBy(() -> service.create(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                createRequest()
        )).isInstanceOf(GradeLevelConflictException.class)
                .hasMessage("A grade level with this code already exists");

        gradeLevelRepositoryStub.duplicateCode = false;
        gradeLevelRepositoryStub.duplicateName = true;

        assertThatThrownBy(() -> service.create(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                createRequest()
        )).isInstanceOf(GradeLevelConflictException.class)
                .hasMessage("A grade level with this name already exists");

        gradeLevelRepositoryStub.duplicateName = false;
        gradeLevelRepositoryStub.duplicateDisplayOrder = true;

        assertThatThrownBy(() -> service.create(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                createRequest()
        )).isInstanceOf(GradeLevelConflictException.class)
                .hasMessage("A grade level with this display order already exists");
    }

    @Test
    void missingAcademicYearIsRejected() {
        academicYearRepositoryStub.foundAcademicYear = Optional.empty();

        assertThatThrownBy(() -> service.getAll(ORGANIZATION_ID, ACADEMIC_YEAR_ID))
                .isInstanceOf(AcademicYearNotFoundException.class)
                .hasMessage("Academic year not found");
    }

    @Test
    void closedAcademicYearCannotBeModified() {
        academicYearRepositoryStub.foundAcademicYear =
                Optional.of(academicYear(AcademicYearStatus.CLOSED));

        assertThatThrownBy(() -> service.create(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                createRequest()
        )).isInstanceOf(GradeLevelConflictException.class)
                .hasMessage("Grade levels can only be modified for a planned or active academic year");
    }

    @Test
    void getByIdIsTenantScoped() {
        gradeLevelRepositoryStub.foundGradeLevel =
                Optional.of(gradeLevel(GRADE_LEVEL_ID, "G1", "Grade 1", 1, 0L));

        GradeLevelResponse response = service.getById(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID
        );

        assertThat(response.id()).isEqualTo(GRADE_LEVEL_ID);
        assertThat(gradeLevelRepositoryStub.lastFindId).isEqualTo(GRADE_LEVEL_ID);
        assertThat(gradeLevelRepositoryStub.lastFindAcademicYearId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(gradeLevelRepositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
    }

    @Test
    void getAllMapsResultsInRepositoryOrder() {
        gradeLevelRepositoryStub.allGradeLevels = List.of(
                gradeLevel(1L, "G1", "Grade 1", 1, 0L),
                gradeLevel(2L, "G2", "Grade 2", 2, 0L)
        );

        List<GradeLevelResponse> responses =
                service.getAll(ORGANIZATION_ID, ACADEMIC_YEAR_ID);

        assertThat(responses).extracting(GradeLevelResponse::code)
                .containsExactly("G1", "G2");
    }

    @Test
    void updateSucceedsAndExcludesCurrentIdFromDuplicateChecks() {
        GradeLevel existing = gradeLevel(GRADE_LEVEL_ID, "G1", "Grade 1", 1, 3L);
        gradeLevelRepositoryStub.foundGradeLevel = Optional.of(existing);
        gradeLevelRepositoryStub.savedGradeLevel = existing;

        GradeLevelResponse response = service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                new UpdateGradeLevelRequest(" g2 ", " Grade 2 ", 2, 3L)
        );

        assertThat(response.code()).isEqualTo("G2");
        assertThat(response.name()).isEqualTo("Grade 2");
        assertThat(response.displayOrder()).isEqualTo(2);
        assertThat(gradeLevelRepositoryStub.lastDuplicateExclusionId)
                .isEqualTo(GRADE_LEVEL_ID);
    }

    @Test
    void updateRejectsStaleVersion() {
        gradeLevelRepositoryStub.foundGradeLevel =
                Optional.of(gradeLevel(GRADE_LEVEL_ID, "G1", "Grade 1", 1, 3L));

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                GRADE_LEVEL_ID,
                updateRequest(2L)
        )).isInstanceOf(GradeLevelConflictException.class)
                .hasMessage("Grade level was modified by another request");
    }

    private static CreateGradeLevelRequest createRequest() {
        return new CreateGradeLevelRequest("G1", "Grade 1", 1);
    }

    private static UpdateGradeLevelRequest updateRequest(Long version) {
        return new UpdateGradeLevelRequest("G1", "Grade 1", 1, version);
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

    private static GradeLevel gradeLevel(
            Long id,
            String code,
            String name,
            Integer displayOrder,
            Long version
    ) {
        GradeLevel gradeLevel = new GradeLevel(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                code,
                name,
                displayOrder
        );
        ReflectionTestUtils.setField(gradeLevel, "id", id);
        ReflectionTestUtils.setField(gradeLevel, "version", version);
        ReflectionTestUtils.setField(
                gradeLevel,
                "createdAt",
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
        ReflectionTestUtils.setField(
                gradeLevel,
                "updatedAt",
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
        return gradeLevel;
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
        private List<GradeLevel> allGradeLevels = List.of();
        private GradeLevel savedGradeLevel;
        private GradeLevel lastSavedGradeLevel;
        private long lastFindId;
        private long lastFindAcademicYearId;
        private long lastFindOrganizationId;
        private long lastDuplicateExclusionId;
        private boolean duplicateCode;
        private boolean duplicateName;
        private boolean duplicateDisplayOrder;

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
                        case "findAllByOrganizationIdAndAcademicYearIdOrderByDisplayOrderAsc" -> allGradeLevels;
                        case "existsByAcademicYearIdAndCodeIgnoreCase" -> duplicateCode;
                        case "existsByAcademicYearIdAndNameIgnoreCase" -> duplicateName;
                        case "existsByAcademicYearIdAndDisplayOrder" -> duplicateDisplayOrder;
                        case "existsByAcademicYearIdAndCodeIgnoreCaseAndIdNot" -> {
                            lastDuplicateExclusionId = (Long) args[2];
                            yield duplicateCode;
                        }
                        case "existsByAcademicYearIdAndNameIgnoreCaseAndIdNot" -> {
                            lastDuplicateExclusionId = (Long) args[2];
                            yield duplicateName;
                        }
                        case "existsByAcademicYearIdAndDisplayOrderAndIdNot" -> {
                            lastDuplicateExclusionId = (Long) args[2];
                            yield duplicateDisplayOrder;
                        }
                        case "saveAndFlush" -> {
                            lastSavedGradeLevel = (GradeLevel) args[0];
                            yield savedGradeLevel != null
                                    ? savedGradeLevel
                                    : lastSavedGradeLevel;
                        }
                        case "toString" -> "GradeLevelRepositoryStub";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
