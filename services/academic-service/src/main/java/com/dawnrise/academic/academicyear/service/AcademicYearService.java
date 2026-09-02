package com.dawnrise.academic.academicyear.service;

import com.dawnrise.academic.academicyear.dto.AcademicYearResponse;
import com.dawnrise.academic.academicyear.dto.CreateAcademicYearRequest;
import com.dawnrise.academic.academicyear.dto.UpdateAcademicYearRequest;
import com.dawnrise.academic.academicyear.dto.VoidAcademicYearRequest;

import java.util.List;

public interface AcademicYearService {

    AcademicYearResponse create(
            long organizationId,
            CreateAcademicYearRequest request
    );

    AcademicYearResponse getById(
            long organizationId,
            long academicYearId
    );

    List<AcademicYearResponse> getAll(
            long organizationId
    );

    AcademicYearResponse getActive(
            long organizationId
    );

    AcademicYearResponse update(
            long organizationId,
            long academicYearId,
            UpdateAcademicYearRequest request
    );

    AcademicYearResponse activate(
            long organizationId,
            long academicYearId
    );

    AcademicYearResponse close(
            long organizationId,
            long academicYearId
    );

    AcademicYearResponse voidYear(
            long organizationId,
            long academicYearId,
            long authenticatedUserId,
            VoidAcademicYearRequest request
    );
}