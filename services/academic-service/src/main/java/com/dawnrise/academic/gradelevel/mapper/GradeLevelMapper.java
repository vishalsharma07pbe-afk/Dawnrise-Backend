package com.dawnrise.academic.gradelevel.mapper;

import com.dawnrise.academic.gradelevel.dto.GradeLevelResponse;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import org.springframework.stereotype.Component;

@Component
public class GradeLevelMapper {

    public GradeLevelResponse toResponse(GradeLevel gradeLevel) {
        return new GradeLevelResponse(
                gradeLevel.getId(),
                gradeLevel.getAcademicYearId(),
                gradeLevel.getCode(),
                gradeLevel.getName(),
                gradeLevel.getDisplayOrder(),
                gradeLevel.getVersion(),
                gradeLevel.getCreatedAt(),
                gradeLevel.getUpdatedAt()
        );
    }
}