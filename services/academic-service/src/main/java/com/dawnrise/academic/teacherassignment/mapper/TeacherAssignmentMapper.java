package com.dawnrise.academic.teacherassignment.mapper;

import com.dawnrise.academic.teacherassignment.dto.TeacherAssignmentResponse;
import com.dawnrise.academic.teacherassignment.entity.TeacherAssignment;
import org.springframework.stereotype.Component;

@Component
public class TeacherAssignmentMapper {

    public TeacherAssignmentResponse toResponse(
            TeacherAssignment assignment
    ) {
        return new TeacherAssignmentResponse(
                assignment.getId(),
                assignment.getAcademicYearId(),
                assignment.getGradeLevelId(),
                assignment.getSectionId(),
                assignment.getGradeLevelSubjectId(),
                assignment.getTeacherUserId(),
                assignment.getAssignmentType(),
                assignment.getVersion(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt()
        );
    }
}