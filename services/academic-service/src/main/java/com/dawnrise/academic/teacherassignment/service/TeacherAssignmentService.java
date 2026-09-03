package com.dawnrise.academic.teacherassignment.service;

import com.dawnrise.academic.teacherassignment.dto.CreateTeacherAssignmentRequest;
import com.dawnrise.academic.teacherassignment.dto.TeacherAssignmentResponse;
import com.dawnrise.academic.teacherassignment.dto.UpdateTeacherAssignmentRequest;

import java.util.List;

public interface TeacherAssignmentService {

    TeacherAssignmentResponse create(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            CreateTeacherAssignmentRequest request
    );

    TeacherAssignmentResponse getById(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            long assignmentId
    );

    List<TeacherAssignmentResponse> getAll(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId
    );

    TeacherAssignmentResponse update(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            long assignmentId,
            UpdateTeacherAssignmentRequest request
    );

    void remove(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            long assignmentId
    );
}