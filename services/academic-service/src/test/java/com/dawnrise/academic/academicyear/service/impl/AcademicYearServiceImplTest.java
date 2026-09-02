package com.dawnrise.academic.academicyear.service.impl;

import com.dawnrise.academic.academicyear.dto.AcademicYearResponse;
import com.dawnrise.academic.academicyear.dto.CreateAcademicYearRequest;
import com.dawnrise.academic.academicyear.dto.UpdateAcademicYearRequest;
import com.dawnrise.academic.academicyear.dto.VoidAcademicYearRequest;
import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.exception.AcademicYearConflictException;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.mapper.AcademicYearMapper;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
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

class AcademicYearServiceImplTest {

    private static final long ORGANIZATION_ID = 10L;
    private static final long ACADEMIC_YEAR_ID = 20L;
    private static final LocalDate START_DATE = LocalDate.of(2026, 4, 1);
    private static final LocalDate END_DATE = LocalDate.of(2027, 3, 31);

    private RepositoryStub repositoryStub;
    private GradeLevelRepositoryStub gradeLevelRepositoryStub;
    private AcademicYearServiceImpl service;

    @BeforeEach
    void setUp() {
        repositoryStub = new RepositoryStub();
        gradeLevelRepositoryStub = new GradeLevelRepositoryStub();
        service = new AcademicYearServiceImpl(
                repositoryStub.repository(),
                gradeLevelRepositoryStub.repository(),
                new AcademicYearMapper()
        );
    }

    @Test
    void createSucceeds() {
        repositoryStub.savedAcademicYear = academicYear(
                ACADEMIC_YEAR_ID,
                "2026-2027",
                START_DATE,
                END_DATE,
                AcademicYearStatus.PLANNED,
                0L
        );

        AcademicYearResponse response = service.create(
                ORGANIZATION_ID,
                new CreateAcademicYearRequest("  2026-2027  ", START_DATE, END_DATE)
        );

        assertThat(response.id()).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(response.name()).isEqualTo("2026-2027");
        assertThat(repositoryStub.lastSavedAcademicYear.getName()).isEqualTo("2026-2027");
    }

    @Test
    void duplicateNameIsRejected() {
        repositoryStub.duplicateName = true;

        assertThatThrownBy(() -> service.create(ORGANIZATION_ID, createRequest()))
                .isInstanceOf(AcademicYearConflictException.class)
                .hasMessage("An academic year with this name already exists");

        assertThat(repositoryStub.saveCalls).isZero();
    }

    @Test
    void overlappingPeriodIsRejected() {
        repositoryStub.overlaps = true;

        assertThatThrownBy(() -> service.create(ORGANIZATION_ID, createRequest()))
                .isInstanceOf(AcademicYearConflictException.class)
                .hasMessage("The academic year overlaps an existing academic year");

        assertThat(repositoryStub.saveCalls).isZero();
    }

    @Test
    void getByIdIsTenantScoped() {
        repositoryStub.foundAcademicYear = Optional.of(academicYear(
                ACADEMIC_YEAR_ID,
                "2026-2027",
                START_DATE,
                END_DATE,
                AcademicYearStatus.PLANNED,
                0L
        ));

        AcademicYearResponse response = service.getById(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID
        );

        assertThat(response.id()).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(repositoryStub.lastFindId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(repositoryStub.lastFindOrganizationId).isEqualTo(ORGANIZATION_ID);
    }

    @Test
    void getByIdHandlesMissingTenantScopedYear() {
        repositoryStub.foundAcademicYear = Optional.empty();

        assertThatThrownBy(() -> service.getById(ORGANIZATION_ID, ACADEMIC_YEAR_ID))
                .isInstanceOf(AcademicYearNotFoundException.class)
                .hasMessage("Academic year not found");
    }

    @Test
    void getAllMapsEveryResult() {
        repositoryStub.allAcademicYears = List.of(
                academicYear(1L, "2027-2028", LocalDate.of(2027, 4, 1),
                        LocalDate.of(2028, 3, 31), AcademicYearStatus.PLANNED, 0L),
                academicYear(2L, "2026-2027", START_DATE, END_DATE,
                        AcademicYearStatus.CLOSED, 1L)
        );

        List<AcademicYearResponse> responses = service.getAll(ORGANIZATION_ID);

        assertThat(responses).extracting(AcademicYearResponse::id)
                .containsExactly(1L, 2L);
        assertThat(responses).extracting(AcademicYearResponse::name)
                .containsExactly("2027-2028", "2026-2027");
    }

    @Test
    void getActiveSucceeds() {
        repositoryStub.activeAcademicYear = Optional.of(academicYear(
                ACADEMIC_YEAR_ID,
                "2026-2027",
                START_DATE,
                END_DATE,
                AcademicYearStatus.ACTIVE,
                1L
        ));

        AcademicYearResponse response = service.getActive(ORGANIZATION_ID);

        assertThat(response.status()).isEqualTo(AcademicYearStatus.ACTIVE);
    }

    @Test
    void getActiveHandlesMissingActiveYear() {
        repositoryStub.activeAcademicYear = Optional.empty();

        assertThatThrownBy(() -> service.getActive(ORGANIZATION_ID))
                .isInstanceOf(AcademicYearNotFoundException.class)
                .hasMessage("Active academic year not found");
    }

    @Test
    void updateSucceeds() {
        AcademicYear existing = plannedAcademicYearWithVersion(3L);
        repositoryStub.foundAcademicYear = Optional.of(existing);
        repositoryStub.savedAcademicYear = existing;
        LocalDate updatedStart = LocalDate.of(2027, 4, 1);
        LocalDate updatedEnd = LocalDate.of(2028, 3, 31);

        AcademicYearResponse response = service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                new UpdateAcademicYearRequest("  2027-2028 ", updatedStart, updatedEnd, 3L)
        );

        assertThat(response.name()).isEqualTo("2027-2028");
        assertThat(response.startDate()).isEqualTo(updatedStart);
        assertThat(response.endDate()).isEqualTo(updatedEnd);
    }

    @Test
    void staleVersionIsRejected() {
        repositoryStub.foundAcademicYear = Optional.of(plannedAcademicYearWithVersion(3L));

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                updateRequest(2L)
        )).isInstanceOf(AcademicYearConflictException.class)
                .hasMessage("Academic year was modified by another request");

        assertThat(repositoryStub.saveCalls).isZero();
    }

    @Test
    void updateDuplicateAndOverlapChecksExcludeCurrentId() {
        AcademicYear existing = plannedAcademicYearWithVersion(3L);
        repositoryStub.foundAcademicYear = Optional.of(existing);
        repositoryStub.savedAcademicYear = existing;

        service.update(ORGANIZATION_ID, ACADEMIC_YEAR_ID, updateRequest(3L));

        assertThat(repositoryStub.lastDuplicateExclusionId).isEqualTo(ACADEMIC_YEAR_ID);
        assertThat(repositoryStub.lastOverlapExclusionId).isEqualTo(ACADEMIC_YEAR_ID);
    }

    @Test
    void updateDuplicateNameIsRejected() {
        repositoryStub.foundAcademicYear = Optional.of(plannedAcademicYearWithVersion(3L));
        repositoryStub.duplicateNameExcludingId = true;

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                updateRequest(3L)
        )).isInstanceOf(AcademicYearConflictException.class)
                .hasMessage("An academic year with this name already exists");
    }

    @Test
    void updateOverlapIsRejected() {
        repositoryStub.foundAcademicYear = Optional.of(plannedAcademicYearWithVersion(3L));
        repositoryStub.overlapsExcludingId = true;

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                updateRequest(3L)
        )).isInstanceOf(AcademicYearConflictException.class)
                .hasMessage("The academic year overlaps an existing academic year");
    }

    @Test
    void onlyPlannedYearsCanUpdate() {
        repositoryStub.foundAcademicYear = Optional.of(academicYear(
                ACADEMIC_YEAR_ID,
                "2026-2027",
                START_DATE,
                END_DATE,
                AcademicYearStatus.ACTIVE,
                3L
        ));

        assertThatThrownBy(() -> service.update(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                updateRequest(3L)
        )).isInstanceOf(AcademicYearConflictException.class)
                .hasMessage("Only a planned academic year can be updated");
    }

    @Test
    void activationSucceeds() {
        AcademicYear existing = academicYear(
                ACADEMIC_YEAR_ID,
                "2026-2027",
                START_DATE,
                END_DATE,
                AcademicYearStatus.PLANNED,
                0L
        );
        repositoryStub.foundAcademicYear = Optional.of(existing);
        repositoryStub.activeAcademicYear = Optional.empty();
        repositoryStub.savedAcademicYear = existing;

        AcademicYearResponse response = service.activate(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID
        );

        assertThat(response.status()).isEqualTo(AcademicYearStatus.ACTIVE);
    }

    @Test
    void activationFailsIfAnotherActiveYearExists() {
        repositoryStub.foundAcademicYear = Optional.of(academicYear(
                ACADEMIC_YEAR_ID,
                "2026-2027",
                START_DATE,
                END_DATE,
                AcademicYearStatus.PLANNED,
                0L
        ));
        repositoryStub.activeAcademicYear = Optional.of(academicYear(
                99L,
                "2025-2026",
                LocalDate.of(2025, 4, 1),
                LocalDate.of(2026, 3, 31),
                AcademicYearStatus.ACTIVE,
                1L
        ));

        assertThatThrownBy(() -> service.activate(ORGANIZATION_ID, ACADEMIC_YEAR_ID))
                .isInstanceOf(AcademicYearConflictException.class)
                .hasMessage("An active academic year already exists");
    }

    @Test
    void closeSucceeds() {
        AcademicYear existing = academicYear(
                ACADEMIC_YEAR_ID,
                "2026-2027",
                START_DATE,
                END_DATE,
                AcademicYearStatus.ACTIVE,
                1L
        );
        repositoryStub.foundAcademicYear = Optional.of(existing);
        repositoryStub.savedAcademicYear = existing;

        AcademicYearResponse response = service.close(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID
        );

        assertThat(response.status()).isEqualTo(AcademicYearStatus.CLOSED);
    }

    @Test
    void invalidTransitionsPropagateCorrectly() {
        repositoryStub.foundAcademicYear = Optional.of(academicYear(
                ACADEMIC_YEAR_ID,
                "2026-2027",
                START_DATE,
                END_DATE,
                AcademicYearStatus.ACTIVE,
                1L
        ));
        repositoryStub.activeAcademicYear = Optional.empty();

        assertThatThrownBy(() -> service.activate(ORGANIZATION_ID, ACADEMIC_YEAR_ID))
                .isInstanceOf(AcademicYearConflictException.class)
                .hasMessage("Only a planned academic year can be activated");
    }

    @Test
    void voidSucceedsForClosedYearWithoutGradeLevels() {
        AcademicYear existing = academicYear(
                ACADEMIC_YEAR_ID,
                "2026-2027",
                START_DATE,
                END_DATE,
                AcademicYearStatus.CLOSED,
                4L
        );
        repositoryStub.foundAcademicYear = Optional.of(existing);
        repositoryStub.savedAcademicYear = existing;

        AcademicYearResponse response = service.voidYear(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                123L,
                new VoidAcademicYearRequest(" Created in error ", 4L)
        );

        assertThat(response.status()).isEqualTo(AcademicYearStatus.VOIDED);
        assertThat(response.voidReason()).isEqualTo("Created in error");
        assertThat(response.voidedAt()).isNotNull();
        assertThat(response.voidedByUserId()).isEqualTo(123L);
        assertThat(gradeLevelRepositoryStub.lastOrganizationId).isEqualTo(ORGANIZATION_ID);
        assertThat(gradeLevelRepositoryStub.lastAcademicYearId).isEqualTo(ACADEMIC_YEAR_ID);
    }

    @Test
    void voidRejectsStaleVersion() {
        repositoryStub.foundAcademicYear = Optional.of(academicYear(
                ACADEMIC_YEAR_ID,
                "2026-2027",
                START_DATE,
                END_DATE,
                AcademicYearStatus.CLOSED,
                4L
        ));

        assertThatThrownBy(() -> service.voidYear(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                123L,
                new VoidAcademicYearRequest("Created in error", 3L)
        )).isInstanceOf(AcademicYearConflictException.class)
                .hasMessage("Academic year was modified by another request");

        assertThat(repositoryStub.saveCalls).isZero();
        assertThat(gradeLevelRepositoryStub.existsCalls).isZero();
    }

    @Test
    void voidRejectsAcademicYearWithGradeLevels() {
        repositoryStub.foundAcademicYear = Optional.of(academicYear(
                ACADEMIC_YEAR_ID,
                "2026-2027",
                START_DATE,
                END_DATE,
                AcademicYearStatus.CLOSED,
                4L
        ));
        gradeLevelRepositoryStub.exists = true;

        assertThatThrownBy(() -> service.voidYear(
                ORGANIZATION_ID,
                ACADEMIC_YEAR_ID,
                123L,
                new VoidAcademicYearRequest("Created in error", 4L)
        )).isInstanceOf(AcademicYearConflictException.class)
                .hasMessage("Academic year cannot be voided because it contains grade levels");

        assertThat(repositoryStub.saveCalls).isZero();
    }

    private static CreateAcademicYearRequest createRequest() {
        return new CreateAcademicYearRequest("2026-2027", START_DATE, END_DATE);
    }

    private static UpdateAcademicYearRequest updateRequest(Long version) {
        return new UpdateAcademicYearRequest(
                "2026-2027",
                START_DATE,
                END_DATE,
                version
        );
    }

    private static AcademicYear plannedAcademicYearWithVersion(Long version) {
        return academicYear(
                ACADEMIC_YEAR_ID,
                "2026-2027",
                START_DATE,
                END_DATE,
                AcademicYearStatus.PLANNED,
                version
        );
    }

    private static AcademicYear academicYear(
            Long id,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            AcademicYearStatus status,
            Long version
    ) {
        AcademicYear academicYear = new AcademicYear(
                ORGANIZATION_ID,
                name,
                startDate,
                endDate
        );
        ReflectionTestUtils.setField(academicYear, "id", id);
        ReflectionTestUtils.setField(academicYear, "status", status);
        ReflectionTestUtils.setField(academicYear, "version", version);
        ReflectionTestUtils.setField(
                academicYear,
                "createdAt",
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
        ReflectionTestUtils.setField(
                academicYear,
                "updatedAt",
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
        return academicYear;
    }

    private static class RepositoryStub {

        private Optional<AcademicYear> foundAcademicYear = Optional.empty();
        private Optional<AcademicYear> activeAcademicYear = Optional.empty();
        private List<AcademicYear> allAcademicYears = List.of();
        private AcademicYear savedAcademicYear;
        private AcademicYear lastSavedAcademicYear;
        private long lastFindId;
        private long lastFindOrganizationId;
        private long lastDuplicateExclusionId;
        private long lastOverlapExclusionId;
        private int saveCalls;
        private boolean duplicateName;
        private boolean duplicateNameExcludingId;
        private boolean overlaps;
        private boolean overlapsExcludingId;

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
                        case "findAllByOrganizationIdOrderByStartDateDesc" -> allAcademicYears;
                        case "findByOrganizationIdAndStatus" -> activeAcademicYear;
                        case "existsByOrganizationIdAndNameIgnoreCaseAndStatusNot" -> duplicateName;
                        case "existsByOrganizationIdAndNameIgnoreCaseAndIdNotAndStatusNot" -> {
                            lastDuplicateExclusionId = (Long) args[2];
                            yield duplicateNameExcludingId;
                        }
                        case "existsOverlappingPeriod" -> overlaps;
                        case "existsOverlappingPeriodExcludingId" -> {
                            lastOverlapExclusionId = (Long) args[1];
                            yield overlapsExcludingId;
                        }
                        case "saveAndFlush" -> {
                            saveCalls++;
                            lastSavedAcademicYear = (AcademicYear) args[0];
                            yield savedAcademicYear != null
                                    ? savedAcademicYear
                                    : lastSavedAcademicYear;
                        }
                        case "toString" -> "RepositoryStub";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static class GradeLevelRepositoryStub {

        private boolean exists;
        private int existsCalls;
        private long lastOrganizationId;
        private long lastAcademicYearId;

        private GradeLevelRepository repository() {
            return (GradeLevelRepository) Proxy.newProxyInstance(
                    GradeLevelRepository.class.getClassLoader(),
                    new Class<?>[]{GradeLevelRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "existsByOrganizationIdAndAcademicYearId" -> {
                            existsCalls++;
                            lastOrganizationId = (Long) args[0];
                            lastAcademicYearId = (Long) args[1];
                            yield exists;
                        }
                        case "toString" -> "GradeLevelRepositoryStub";
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
