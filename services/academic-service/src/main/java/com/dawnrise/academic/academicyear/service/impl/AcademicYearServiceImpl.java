package com.dawnrise.academic.academicyear.service.impl;

import com.dawnrise.academic.academicyear.dto.AcademicYearResponse;
import com.dawnrise.academic.academicyear.dto.CreateAcademicYearRequest;
import com.dawnrise.academic.academicyear.dto.UpdateAcademicYearRequest;
import com.dawnrise.academic.academicyear.mapper.AcademicYearMapper;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.academicyear.service.AcademicYearService;
import com.dawnrise.academic.academicyear.dto.AcademicYearResponse;
import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.dto.CreateAcademicYearRequest;
import com.dawnrise.academic.academicyear.exception.AcademicYearConflictException;
import com.dawnrise.academic.academicyear.exception.InvalidAcademicYearException;

import java.time.LocalDate;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AcademicYearServiceImpl implements AcademicYearService {

    private final AcademicYearRepository academicYearRepository;
    private final AcademicYearMapper academicYearMapper;

    public AcademicYearServiceImpl(
            AcademicYearRepository academicYearRepository,
            AcademicYearMapper academicYearMapper
    ) {
        this.academicYearRepository = academicYearRepository;
        this.academicYearMapper = academicYearMapper;
    }

    @Override
    public AcademicYearResponse create(
            long organizationId,
            CreateAcademicYearRequest request
    ) {
        String normalizedName = normalizeName(request.name());

        validateDates(
                request.startDate(),
                request.endDate()
        );

        if (academicYearRepository
                .existsByOrganizationIdAndNameIgnoreCase(
                        organizationId,
                        normalizedName
                )) {
            throw new AcademicYearConflictException(
                    "An academic year with this name already exists"
            );
        }

        if (academicYearRepository.existsOverlappingPeriod(
                organizationId,
                request.startDate(),
                request.endDate()
        )) {
            throw new AcademicYearConflictException(
                    "The academic year overlaps an existing academic year"
            );
        }

        AcademicYear academicYear = new AcademicYear(
                organizationId,
                normalizedName,
                request.startDate(),
                request.endDate()
        );

        AcademicYear savedAcademicYear =
                academicYearRepository.saveAndFlush(academicYear);

        return academicYearMapper.toResponse(savedAcademicYear);
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicYearResponse getById(
            long organizationId,
            long academicYearId
    ) {
        AcademicYear academicYear = findAcademicYear(
                organizationId,
                academicYearId
        );

        return academicYearMapper.toResponse(academicYear);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AcademicYearResponse> getAll(long organizationId) {
        return academicYearRepository
                .findAllByOrganizationIdOrderByStartDateDesc(
                        organizationId
                )
                .stream()
                .map(academicYearMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicYearResponse getActive(long organizationId) {
        AcademicYear academicYear = academicYearRepository
                .findByOrganizationIdAndStatus(
                        organizationId,
                        AcademicYearStatus.ACTIVE
                )
                .orElseThrow(() ->
                        new AcademicYearNotFoundException(
                                "Active academic year not found"
                        )
                );

        return academicYearMapper.toResponse(academicYear);
    }

    @Override
    public AcademicYearResponse update(
            long organizationId,
            long academicYearId,
            UpdateAcademicYearRequest request
    ) {
        AcademicYear academicYear = findAcademicYear(
                organizationId,
                academicYearId
        );

        if (academicYear.getStatus() != AcademicYearStatus.PLANNED) {
            throw new AcademicYearConflictException(
                    "Only a planned academic year can be updated"
            );
        }

        if (!Objects.equals(
                academicYear.getVersion(),
                request.version()
        )) {
            throw new AcademicYearConflictException(
                    "Academic year was modified by another request"
            );
        }

        String normalizedName = normalizeName(request.name());

        validateDates(
                request.startDate(),
                request.endDate()
        );

        if (academicYearRepository
                .existsByOrganizationIdAndNameIgnoreCaseAndIdNot(
                        organizationId,
                        normalizedName,
                        academicYearId
                )) {
            throw new AcademicYearConflictException(
                    "An academic year with this name already exists"
            );
        }

        if (academicYearRepository
                .existsOverlappingPeriodExcludingId(
                        organizationId,
                        academicYearId,
                        request.startDate(),
                        request.endDate()
                )) {
            throw new AcademicYearConflictException(
                    "The academic year overlaps an existing academic year"
            );
        }

        academicYear.updateDetails(
                normalizedName,
                request.startDate(),
                request.endDate()
        );

        AcademicYear savedAcademicYear =
                academicYearRepository.saveAndFlush(academicYear);

        return academicYearMapper.toResponse(savedAcademicYear);
    }

    @Override
    public AcademicYearResponse activate(
            long organizationId,
            long academicYearId
    ) {
        AcademicYear academicYear = findAcademicYear(
                organizationId,
                academicYearId
        );

        academicYearRepository
                .findByOrganizationIdAndStatus(
                        organizationId,
                        AcademicYearStatus.ACTIVE
                )
                .ifPresent(existingActiveYear -> {
                    throw new AcademicYearConflictException(
                            "An active academic year already exists"
                    );
                });

        academicYear.activate();

        AcademicYear savedAcademicYear =
                academicYearRepository.saveAndFlush(academicYear);

        return academicYearMapper.toResponse(savedAcademicYear);
    }

    @Override
    public AcademicYearResponse close(
            long organizationId,
            long academicYearId
    ) {
        AcademicYear academicYear = findAcademicYear(
                organizationId,
                academicYearId
        );

        academicYear.close();

        AcademicYear savedAcademicYear =
                academicYearRepository.saveAndFlush(academicYear);

        return academicYearMapper.toResponse(savedAcademicYear);
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

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidAcademicYearException(
                    "Academic year name is required"
            );
        }

        String normalizedName = name.trim();

        if (normalizedName.length() > 50) {
            throw new InvalidAcademicYearException(
                    "Academic year name cannot exceed 50 characters"
            );
        }

        return normalizedName;
    }

    private void validateDates(
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (startDate == null || endDate == null) {
            throw new InvalidAcademicYearException(
                    "Start date and end date are required"
            );
        }

        if (!startDate.isBefore(endDate)) {
            throw new InvalidAcademicYearException(
                    "Start date must be before end date"
            );
        }
    }
}