package com.dawnrise.academic.subject.service;

import com.dawnrise.academic.subject.dto.BulkCreateSubjectsRequest;
import com.dawnrise.academic.subject.dto.CreateSubjectRequest;
import com.dawnrise.academic.subject.dto.SubjectResponse;
import com.dawnrise.academic.subject.dto.UpdateSubjectRequest;

import java.util.List;

public interface SubjectService {

    SubjectResponse create(
            long organizationId,
            long academicYearId,
            CreateSubjectRequest request
    );

    List<SubjectResponse> createBulk(
            long organizationId,
            long academicYearId,
            BulkCreateSubjectsRequest request
    );

    SubjectResponse getById(
            long organizationId,
            long academicYearId,
            long subjectId
    );

    List<SubjectResponse> getAll(
            long organizationId,
            long academicYearId
    );

    SubjectResponse update(
            long organizationId,
            long academicYearId,
            long subjectId,
            UpdateSubjectRequest request
    );
}
