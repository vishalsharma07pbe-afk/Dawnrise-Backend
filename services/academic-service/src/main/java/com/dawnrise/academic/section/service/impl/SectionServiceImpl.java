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
import com.dawnrise.academic.section.service.SectionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class SectionServiceImpl implements SectionService {

    private final AcademicYearRepository academicYearRepository;
    private final GradeLevelRepository gradeLevelRepository;
    private final SectionRepository sectionRepository;
    private final SectionMapper sectionMapper;

    public SectionServiceImpl(
            AcademicYearRepository academicYearRepository,
            GradeLevelRepository gradeLevelRepository,
            SectionRepository sectionRepository,
            SectionMapper sectionMapper
    ) {
        this.academicYearRepository = academicYearRepository;
        this.gradeLevelRepository = gradeLevelRepository;
        this.sectionRepository = sectionRepository;
        this.sectionMapper = sectionMapper;
    }

    @Override
    public SectionResponse create(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            CreateSectionRequest request
    ) {
        AcademicYear academicYear = findAcademicYear(
                organizationId,
                academicYearId
        );

        ensureAcademicYearIsModifiable(academicYear);

        findGradeLevel(
                organizationId,
                academicYearId,
                gradeLevelId
        );

        Section section = new Section(
                organizationId,
                academicYearId,
                gradeLevelId,
                request.code(),
                request.name(),
                request.displayOrder()
        );

        if (sectionRepository
                .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndCodeIgnoreCase(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        section.getCode()
                )) {
            throw new SectionConflictException(
                    "A section with this code already exists"
            );
        }

        if (sectionRepository
                .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndNameIgnoreCase(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        section.getName()
                )) {
            throw new SectionConflictException(
                    "A section with this name already exists"
            );
        }

        if (sectionRepository
                .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndDisplayOrder(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        section.getDisplayOrder()
                )) {
            throw new SectionConflictException(
                    "A section with this display order already exists"
            );
        }

        Section savedSection =
                sectionRepository.saveAndFlush(section);

        return sectionMapper.toResponse(savedSection);
    }

    @Override
    public List<SectionResponse> createBulk(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            BulkCreateSectionsRequest request
    ) {
        AcademicYear academicYear = findAcademicYear(
                organizationId,
                academicYearId
        );

        ensureAcademicYearIsModifiable(academicYear);

        findGradeLevel(
                organizationId,
                academicYearId,
                gradeLevelId
        );

        List<Section> proposedSections = request.sections()
                .stream()
                .map(item -> new Section(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        item.code(),
                        item.name(),
                        item.displayOrder()
                ))
                .toList();

        Set<String> requestCodes = new HashSet<>();
        Set<String> requestNames = new HashSet<>();
        Set<Integer> requestDisplayOrders = new HashSet<>();

        for (Section section : proposedSections) {
            if (!requestCodes.add(section.getCode())) {
                throw new SectionConflictException(
                        "The bulk request contains a duplicate section code"
                );
            }

            String normalizedName =
                    section.getName().toLowerCase(Locale.ROOT);

            if (!requestNames.add(normalizedName)) {
                throw new SectionConflictException(
                        "The bulk request contains a duplicate section name"
                );
            }

            if (!requestDisplayOrders.add(
                    section.getDisplayOrder()
            )) {
                throw new SectionConflictException(
                        "The bulk request contains a duplicate display order"
                );
            }
        }

        for (Section section : proposedSections) {
            if (sectionRepository
                    .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndCodeIgnoreCase(
                            organizationId,
                            academicYearId,
                            gradeLevelId,
                            section.getCode()
                    )) {
                throw new SectionConflictException(
                        "A section with code "
                                + section.getCode()
                                + " already exists"
                );
            }

            if (sectionRepository
                    .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndNameIgnoreCase(
                            organizationId,
                            academicYearId,
                            gradeLevelId,
                            section.getName()
                    )) {
                throw new SectionConflictException(
                        "A section with name "
                                + section.getName()
                                + " already exists"
                );
            }

            if (sectionRepository
                    .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndDisplayOrder(
                            organizationId,
                            academicYearId,
                            gradeLevelId,
                            section.getDisplayOrder()
                    )) {
                throw new SectionConflictException(
                        "Display order "
                                + section.getDisplayOrder()
                                + " already exists"
                );
            }
        }

        List<Section> savedSections =
                sectionRepository.saveAll(proposedSections);

        sectionRepository.flush();

        return savedSections
                .stream()
                .map(sectionMapper::toResponse)
                .toList();
    }

    @Override
    public List<SectionResponse> applyStructure(
            long organizationId,
            long academicYearId,
            ApplySectionStructureRequest request
    ) {
        AcademicYear academicYear = findAcademicYear(
                organizationId,
                academicYearId
        );

        ensureAcademicYearIsModifiable(academicYear);

        Set<Long> uniqueGradeLevelIds = new HashSet<>();

        /*
         * Validate every target grade before creating anything.
         */
        for (Long gradeLevelId : request.gradeLevelIds()) {
            if (!uniqueGradeLevelIds.add(gradeLevelId)) {
                throw new SectionConflictException(
                        "The request contains a duplicate grade level ID"
                );
            }

            findGradeLevel(
                    organizationId,
                    academicYearId,
                    gradeLevelId
            );
        }

        BulkCreateSectionsRequest bulkRequest =
                new BulkCreateSectionsRequest(
                        request.sections()
                );

        List<SectionResponse> createdSections =
                new ArrayList<>();

        /*
         * Apply the same structure independently to every selected grade.
         */
        for (Long gradeLevelId : request.gradeLevelIds()) {
            createdSections.addAll(
                    createBulk(
                            organizationId,
                            academicYearId,
                            gradeLevelId,
                            bulkRequest
                    )
            );
        }

        return List.copyOf(createdSections);
    }

    @Override
    @Transactional(readOnly = true)
    public SectionResponse getById(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId
    ) {
        findAcademicYear(
                organizationId,
                academicYearId
        );

        findGradeLevel(
                organizationId,
                academicYearId,
                gradeLevelId
        );

        Section section = findSection(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId
        );

        return sectionMapper.toResponse(section);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SectionResponse> getAll(
            long organizationId,
            long academicYearId,
            long gradeLevelId
    ) {
        findAcademicYear(
                organizationId,
                academicYearId
        );

        findGradeLevel(
                organizationId,
                academicYearId,
                gradeLevelId
        );

        return sectionRepository
                .findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdOrderByDisplayOrderAsc(
                        organizationId,
                        academicYearId,
                        gradeLevelId
                )
                .stream()
                .map(sectionMapper::toResponse)
                .toList();
    }

    @Override
    public SectionResponse update(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            UpdateSectionRequest request
    ) {
        AcademicYear academicYear = findAcademicYear(
                organizationId,
                academicYearId
        );

        ensureAcademicYearIsModifiable(academicYear);

        findGradeLevel(
                organizationId,
                academicYearId,
                gradeLevelId
        );

        Section section = findSection(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId
        );

        if (!Objects.equals(
                section.getVersion(),
                request.version()
        )) {
            throw new SectionConflictException(
                    "Section was modified by another request"
            );
        }

        /*
         * This temporary entity validates and normalizes the proposed
         * values. It is not persisted.
         */
        Section proposedSection = new Section(
                organizationId,
                academicYearId,
                gradeLevelId,
                request.code(),
                request.name(),
                request.displayOrder()
        );

        if (sectionRepository
                .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndCodeIgnoreCaseAndIdNot(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        proposedSection.getCode(),
                        sectionId
                )) {
            throw new SectionConflictException(
                    "A section with this code already exists"
            );
        }

        if (sectionRepository
                .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndNameIgnoreCaseAndIdNot(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        proposedSection.getName(),
                        sectionId
                )) {
            throw new SectionConflictException(
                    "A section with this name already exists"
            );
        }

        if (sectionRepository
                .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndDisplayOrderAndIdNot(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        proposedSection.getDisplayOrder(),
                        sectionId
                )) {
            throw new SectionConflictException(
                    "A section with this display order already exists"
            );
        }

        section.updateDetails(
                proposedSection.getCode(),
                proposedSection.getName(),
                proposedSection.getDisplayOrder()
        );

        Section savedSection =
                sectionRepository.saveAndFlush(section);

        return sectionMapper.toResponse(savedSection);
    }

    private Section findSection(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId
    ) {
        return sectionRepository
                .findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                        sectionId,
                        gradeLevelId,
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new SectionNotFoundException(
                                "Section not found"
                        )
                );
    }

    private AcademicYear findAcademicYear(
            long organizationId,
            long academicYearId
    ) {
        return academicYearRepository
                .findByIdAndOrganizationId(
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new AcademicYearNotFoundException(
                                "Academic year not found"
                        )
                );
    }

    private GradeLevel findGradeLevel(
            long organizationId,
            long academicYearId,
            long gradeLevelId
    ) {
        return gradeLevelRepository
                .findByIdAndAcademicYearIdAndOrganizationId(
                        gradeLevelId,
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new GradeLevelNotFoundException(
                                "Grade level not found"
                        )
                );
    }

    private void ensureAcademicYearIsModifiable(
            AcademicYear academicYear
    ) {
        if (academicYear.getStatus() != AcademicYearStatus.PLANNED
                && academicYear.getStatus() != AcademicYearStatus.ACTIVE) {
            throw new SectionConflictException(
                    "Sections can only be modified for a planned or active academic year"
            );
        }
    }
}