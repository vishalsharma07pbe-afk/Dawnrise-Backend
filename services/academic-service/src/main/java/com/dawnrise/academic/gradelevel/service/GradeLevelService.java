package com.dawnrise.academic.gradelevel.service;

import com.dawnrise.academic.gradelevel.dto.CreateGradeLevelRequest;
import com.dawnrise.academic.gradelevel.dto.GradeLevelResponse;
import com.dawnrise.academic.gradelevel.dto.UpdateGradeLevelRequest;

import java.util.List;

public interface GradeLevelService {

    GradeLevelResponse create(
            long organizationId,
            long academicYearId,
            CreateGradeLevelRequest request
    );

    GradeLevelResponse getById(
            long organizationId,
            long academicYearId,
            long gradeLevelId
    );

    List<GradeLevelResponse> getAll(
            long organizationId,
            long academicYearId
    );

    GradeLevelResponse update(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            UpdateGradeLevelRequest request
    );
}