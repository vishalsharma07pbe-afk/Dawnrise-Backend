package com.dawnrise.academic.gradelevelsubject.service;

import com.dawnrise.academic.gradelevelsubject.dto.CreateGradeLevelSubjectRequest;
import com.dawnrise.academic.gradelevelsubject.dto.GradeLevelSubjectResponse;
import com.dawnrise.academic.gradelevelsubject.dto.UpdateGradeLevelSubjectRequest;

import java.util.List;

public interface GradeLevelSubjectService {

    GradeLevelSubjectResponse assign(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            CreateGradeLevelSubjectRequest request
    );

    GradeLevelSubjectResponse getById(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long assignmentId
    );

    List<GradeLevelSubjectResponse> getAll(
            long organizationId,
            long academicYearId,
            long gradeLevelId
    );

    GradeLevelSubjectResponse update(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long assignmentId,
            UpdateGradeLevelSubjectRequest request
    );

    void remove(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long assignmentId
    );
}