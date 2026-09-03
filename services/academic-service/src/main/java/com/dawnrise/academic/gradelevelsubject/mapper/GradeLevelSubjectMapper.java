package com.dawnrise.academic.gradelevelsubject.mapper;

import com.dawnrise.academic.gradelevelsubject.dto.GradeLevelSubjectResponse;
import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import org.springframework.stereotype.Component;

@Component
public class GradeLevelSubjectMapper {

    public GradeLevelSubjectResponse toResponse(
            GradeLevelSubject assignment
    ) {
        return new GradeLevelSubjectResponse(
                assignment.getId(),
                assignment.getAcademicYearId(),
                assignment.getGradeLevelId(),
                assignment.getSubjectId(),
                assignment.getMandatory(),
                assignment.getDisplayOrder(),
                assignment.getVersion(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt()
        );
    }
}