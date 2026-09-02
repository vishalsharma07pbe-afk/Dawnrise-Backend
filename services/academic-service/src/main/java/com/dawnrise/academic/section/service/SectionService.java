package com.dawnrise.academic.section.service;

import com.dawnrise.academic.section.dto.CreateSectionRequest;
import com.dawnrise.academic.section.dto.SectionResponse;
import com.dawnrise.academic.section.dto.UpdateSectionRequest;
import com.dawnrise.academic.section.dto.BulkCreateSectionsRequest;
import com.dawnrise.academic.section.dto.ApplySectionStructureRequest;

import java.util.List;

public interface SectionService {

    SectionResponse create(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            CreateSectionRequest request
    );

    List<SectionResponse> createBulk(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            BulkCreateSectionsRequest request
    );

    List<SectionResponse> applyStructure(
            long organizationId,
            long academicYearId,
            ApplySectionStructureRequest request
    );

    SectionResponse getById(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId
    );

    List<SectionResponse> getAll(
            long organizationId,
            long academicYearId,
            long gradeLevelId
    );

    SectionResponse update(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            UpdateSectionRequest request
    );
}