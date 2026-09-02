package com.dawnrise.academic.gradelevel.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.dto.CreateGradeLevelRequest;
import com.dawnrise.academic.gradelevel.dto.GradeLevelResponse;
import com.dawnrise.academic.gradelevel.dto.UpdateGradeLevelRequest;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.exception.GradeLevelNotFoundException;
import com.dawnrise.academic.gradelevel.mapper.GradeLevelMapper;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.gradelevel.exception.GradeLevelConflictException;
import com.dawnrise.academic.gradelevel.dto.BulkCreateGradeLevelsRequest;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Objects;

import com.dawnrise.academic.gradelevel.service.GradeLevelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class GradeLevelServiceImpl implements GradeLevelService {

    private final GradeLevelRepository gradeLevelRepository;
    private final AcademicYearRepository academicYearRepository;
    private final GradeLevelMapper gradeLevelMapper;

    public GradeLevelServiceImpl(
            GradeLevelRepository gradeLevelRepository,
            AcademicYearRepository academicYearRepository,
            GradeLevelMapper gradeLevelMapper
    ) {
        this.gradeLevelRepository = gradeLevelRepository;
        this.academicYearRepository = academicYearRepository;
        this.gradeLevelMapper = gradeLevelMapper;
    }

    @Override
    public GradeLevelResponse create(
            long organizationId,
            long academicYearId,
            CreateGradeLevelRequest request
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);

        GradeLevel gradeLevel = new GradeLevel(
                organizationId,
                academicYearId,
                request.code(),
                request.name(),
                request.displayOrder()
        );

        if (gradeLevelRepository
                .existsByAcademicYearIdAndCodeIgnoreCase(
                        academicYearId,
                        gradeLevel.getCode()
                )) {
            throw new GradeLevelConflictException(
                    "A grade level with this code already exists"
            );
        }

        if (gradeLevelRepository
                .existsByAcademicYearIdAndNameIgnoreCase(
                        academicYearId,
                        gradeLevel.getName()
                )) {
            throw new GradeLevelConflictException(
                    "A grade level with this name already exists"
            );
        }

        if (gradeLevelRepository
                .existsByAcademicYearIdAndDisplayOrder(
                        academicYearId,
                        gradeLevel.getDisplayOrder()
                )) {
            throw new GradeLevelConflictException(
                    "A grade level with this display order already exists"
            );
        }

        GradeLevel savedGradeLevel =
                gradeLevelRepository.saveAndFlush(gradeLevel);

        return gradeLevelMapper.toResponse(savedGradeLevel);
    }

    @Override
    public List<GradeLevelResponse> createBulk(
            long organizationId,
            long academicYearId,
            BulkCreateGradeLevelsRequest request
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);

        List<GradeLevel> proposedGradeLevels = request.gradeLevels()
                .stream()
                .map(item -> new GradeLevel(
                        organizationId,
                        academicYearId,
                        item.code(),
                        item.name(),
                        item.displayOrder()
                ))
                .toList();

        Set<String> requestCodes = new HashSet<>();
        Set<String> requestNames = new HashSet<>();
        Set<Integer> requestDisplayOrders = new HashSet<>();

        for (GradeLevel gradeLevel : proposedGradeLevels) {
            if (!requestCodes.add(gradeLevel.getCode())) {
                throw new GradeLevelConflictException(
                        "The bulk request contains a duplicate grade level code"
                );
            }

            String normalizedName =
                    gradeLevel.getName().toLowerCase(Locale.ROOT);

            if (!requestNames.add(normalizedName)) {
                throw new GradeLevelConflictException(
                        "The bulk request contains a duplicate grade level name"
                );
            }

            if (!requestDisplayOrders.add(
                    gradeLevel.getDisplayOrder()
            )) {
                throw new GradeLevelConflictException(
                        "The bulk request contains a duplicate display order"
                );
            }
        }

        for (GradeLevel gradeLevel : proposedGradeLevels) {
            if (gradeLevelRepository
                    .existsByAcademicYearIdAndCodeIgnoreCase(
                            academicYearId,
                            gradeLevel.getCode()
                    )) {
                throw new GradeLevelConflictException(
                        "A grade level with code "
                                + gradeLevel.getCode()
                                + " already exists"
                );
            }

            if (gradeLevelRepository
                    .existsByAcademicYearIdAndNameIgnoreCase(
                            academicYearId,
                            gradeLevel.getName()
                    )) {
                throw new GradeLevelConflictException(
                        "A grade level with name "
                                + gradeLevel.getName()
                                + " already exists"
                );
            }

            if (gradeLevelRepository
                    .existsByAcademicYearIdAndDisplayOrder(
                            academicYearId,
                            gradeLevel.getDisplayOrder()
                    )) {
                throw new GradeLevelConflictException(
                        "Display order "
                                + gradeLevel.getDisplayOrder()
                                + " already exists"
                );
            }
        }

        List<GradeLevel> savedGradeLevels =
                gradeLevelRepository.saveAll(proposedGradeLevels);

        gradeLevelRepository.flush();

        return savedGradeLevels
                .stream()
                .map(gradeLevelMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GradeLevelResponse getById(
            long organizationId,
            long academicYearId,
            long gradeLevelId
    ) {
        return gradeLevelMapper.toResponse(
                findGradeLevel(
                        organizationId,
                        academicYearId,
                        gradeLevelId
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<GradeLevelResponse> getAll(
            long organizationId,
            long academicYearId
    ) {
        findAcademicYear(organizationId, academicYearId);

        return gradeLevelRepository
                .findAllByOrganizationIdAndAcademicYearIdOrderByDisplayOrderAsc(
                        organizationId,
                        academicYearId
                )
                .stream()
                .map(gradeLevelMapper::toResponse)
                .toList();
    }

    @Override
    public GradeLevelResponse update(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            UpdateGradeLevelRequest request
    ) {
        AcademicYear academicYear =
                findAcademicYear(organizationId, academicYearId);

        ensureAcademicYearIsModifiable(academicYear);

        GradeLevel gradeLevel = findGradeLevel(
                organizationId,
                academicYearId,
                gradeLevelId
        );

        if (!Objects.equals(
                gradeLevel.getVersion(),
                request.version()
        )) {
            throw new GradeLevelConflictException(
                    "Grade level was modified by another request"
            );
        }

        /*
         * A temporary entity validates and normalizes the proposed values.
         * It is never saved.
         */
        GradeLevel proposedGradeLevel = new GradeLevel(
                organizationId,
                academicYearId,
                request.code(),
                request.name(),
                request.displayOrder()
        );

        if (gradeLevelRepository
                .existsByAcademicYearIdAndCodeIgnoreCaseAndIdNot(
                        academicYearId,
                        proposedGradeLevel.getCode(),
                        gradeLevelId
                )) {
            throw new GradeLevelConflictException(
                    "A grade level with this code already exists"
            );
        }

        if (gradeLevelRepository
                .existsByAcademicYearIdAndNameIgnoreCaseAndIdNot(
                        academicYearId,
                        proposedGradeLevel.getName(),
                        gradeLevelId
                )) {
            throw new GradeLevelConflictException(
                    "A grade level with this name already exists"
            );
        }

        if (gradeLevelRepository
                .existsByAcademicYearIdAndDisplayOrderAndIdNot(
                        academicYearId,
                        proposedGradeLevel.getDisplayOrder(),
                        gradeLevelId
                )) {
            throw new GradeLevelConflictException(
                    "A grade level with this display order already exists"
            );
        }

        gradeLevel.updateDetails(
                proposedGradeLevel.getCode(),
                proposedGradeLevel.getName(),
                proposedGradeLevel.getDisplayOrder()
        );

        GradeLevel savedGradeLevel =
                gradeLevelRepository.saveAndFlush(gradeLevel);

        return gradeLevelMapper.toResponse(savedGradeLevel);
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
            throw new GradeLevelConflictException(
                    "Grade levels can only be modified for a planned or active academic year"
            );
        }
    }
}