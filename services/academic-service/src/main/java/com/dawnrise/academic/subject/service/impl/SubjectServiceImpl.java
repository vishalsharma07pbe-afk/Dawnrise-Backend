package com.dawnrise.academic.subject.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.subject.dto.BulkCreateSubjectsRequest;
import com.dawnrise.academic.subject.dto.CreateSubjectRequest;
import com.dawnrise.academic.subject.dto.SubjectResponse;
import com.dawnrise.academic.subject.dto.UpdateSubjectRequest;
import com.dawnrise.academic.subject.entity.Subject;
import com.dawnrise.academic.subject.exception.SubjectConflictException;
import com.dawnrise.academic.subject.exception.SubjectNotFoundException;
import com.dawnrise.academic.subject.mapper.SubjectMapper;
import com.dawnrise.academic.subject.repository.SubjectRepository;
import com.dawnrise.academic.subject.service.SubjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional
public class SubjectServiceImpl implements SubjectService {

    private final AcademicYearRepository academicYearRepository;
    private final SubjectRepository subjectRepository;
    private final SubjectMapper subjectMapper;

    public SubjectServiceImpl(
            AcademicYearRepository academicYearRepository,
            SubjectRepository subjectRepository,
            SubjectMapper subjectMapper
    ) {
        this.academicYearRepository = academicYearRepository;
        this.subjectRepository = subjectRepository;
        this.subjectMapper = subjectMapper;
    }

    @Override
    public SubjectResponse create(
            long organizationId,
            long academicYearId,
            CreateSubjectRequest request
    ) {
        AcademicYear academicYear = findAcademicYear(
                organizationId,
                academicYearId
        );

        ensureAcademicYearIsModifiable(academicYear);

        Subject subject = new Subject(
                organizationId,
                academicYearId,
                request.code(),
                request.name(),
                request.description()
        );

        if (subjectRepository
                .existsByAcademicYearIdAndCodeIgnoreCase(
                        academicYearId,
                        subject.getCode()
                )) {
            throw new SubjectConflictException(
                    "A subject with this code already exists"
            );
        }

        if (subjectRepository
                .existsByAcademicYearIdAndNameIgnoreCase(
                        academicYearId,
                        subject.getName()
                )) {
            throw new SubjectConflictException(
                    "A subject with this name already exists"
            );
        }

        Subject savedSubject =
                subjectRepository.saveAndFlush(subject);

        return subjectMapper.toResponse(savedSubject);
    }

    @Override
    public List<SubjectResponse> createBulk(
            long organizationId,
            long academicYearId,
            BulkCreateSubjectsRequest request
    ) {
        AcademicYear academicYear = findAcademicYear(
                organizationId,
                academicYearId
        );

        ensureAcademicYearIsModifiable(academicYear);

        List<Subject> proposedSubjects = request.subjects()
                .stream()
                .map(item -> new Subject(
                        organizationId,
                        academicYearId,
                        item.code(),
                        item.name(),
                        item.description()
                ))
                .toList();

        Set<String> requestCodes = new HashSet<>();
        Set<String> requestNames = new HashSet<>();

        for (Subject subject : proposedSubjects) {
            if (!requestCodes.add(subject.getCode())) {
                throw new SubjectConflictException(
                        "The bulk request contains a duplicate subject code"
                );
            }

            if (!requestNames.add(
                    subject.getName().toLowerCase(Locale.ROOT)
            )) {
                throw new SubjectConflictException(
                        "The bulk request contains a duplicate subject name"
                );
            }
        }

        for (Subject subject : proposedSubjects) {
            if (subjectRepository
                    .existsByAcademicYearIdAndCodeIgnoreCase(
                            academicYearId,
                            subject.getCode()
                    )) {
                throw new SubjectConflictException(
                        "A subject with code "
                                + subject.getCode()
                                + " already exists"
                );
            }

            if (subjectRepository
                    .existsByAcademicYearIdAndNameIgnoreCase(
                            academicYearId,
                            subject.getName()
                    )) {
                throw new SubjectConflictException(
                        "A subject with name "
                                + subject.getName()
                                + " already exists"
                );
            }
        }

        return subjectRepository.saveAllAndFlush(proposedSubjects)
                .stream()
                .map(subjectMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SubjectResponse getById(
            long organizationId,
            long academicYearId,
            long subjectId
    ) {
        findAcademicYear(organizationId, academicYearId);

        return subjectMapper.toResponse(
                findSubject(
                        organizationId,
                        academicYearId,
                        subjectId
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectResponse> getAll(
            long organizationId,
            long academicYearId
    ) {
        findAcademicYear(organizationId, academicYearId);

        return subjectRepository
                .findAllByOrganizationIdAndAcademicYearIdOrderByNameAsc(
                        organizationId,
                        academicYearId
                )
                .stream()
                .map(subjectMapper::toResponse)
                .toList();
    }

    @Override
    public SubjectResponse update(
            long organizationId,
            long academicYearId,
            long subjectId,
            UpdateSubjectRequest request
    ) {
        AcademicYear academicYear = findAcademicYear(
                organizationId,
                academicYearId
        );

        ensureAcademicYearIsModifiable(academicYear);

        Subject subject = findSubject(
                organizationId,
                academicYearId,
                subjectId
        );

        if (!Objects.equals(
                subject.getVersion(),
                request.version()
        )) {
            throw new SubjectConflictException(
                    "Subject was modified by another request"
            );
        }

        Subject proposedSubject = new Subject(
                organizationId,
                academicYearId,
                request.code(),
                request.name(),
                request.description()
        );

        if (subjectRepository
                .existsByAcademicYearIdAndCodeIgnoreCaseAndIdNot(
                        academicYearId,
                        proposedSubject.getCode(),
                        subjectId
                )) {
            throw new SubjectConflictException(
                    "A subject with this code already exists"
            );
        }

        if (subjectRepository
                .existsByAcademicYearIdAndNameIgnoreCaseAndIdNot(
                        academicYearId,
                        proposedSubject.getName(),
                        subjectId
                )) {
            throw new SubjectConflictException(
                    "A subject with this name already exists"
            );
        }

        subject.updateDetails(
                proposedSubject.getCode(),
                proposedSubject.getName(),
                proposedSubject.getDescription()
        );

        Subject savedSubject =
                subjectRepository.saveAndFlush(subject);

        return subjectMapper.toResponse(savedSubject);
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

    private Subject findSubject(
            long organizationId,
            long academicYearId,
            long subjectId
    ) {
        return subjectRepository
                .findByIdAndAcademicYearIdAndOrganizationId(
                        subjectId,
                        academicYearId,
                        organizationId
                )
                .orElseThrow(() ->
                        new SubjectNotFoundException(
                                "Subject not found"
                        )
                );
    }

    private void ensureAcademicYearIsModifiable(
            AcademicYear academicYear
    ) {
        if (academicYear.getStatus() != AcademicYearStatus.PLANNED
                && academicYear.getStatus() != AcademicYearStatus.ACTIVE) {
            throw new SubjectConflictException(
                    "Subjects can only be modified for a planned or active academic year"
            );
        }
    }
}
