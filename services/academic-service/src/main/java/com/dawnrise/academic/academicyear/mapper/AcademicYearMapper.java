package com.dawnrise.academic.academicyear.mapper;

import com.dawnrise.academic.academicyear.dto.AcademicYearResponse;
import com.dawnrise.academic.academicyear.entity.AcademicYear;
import org.springframework.stereotype.Component;

@Component
public class AcademicYearMapper {

    public AcademicYearResponse toResponse(AcademicYear academicYear) {
        return new AcademicYearResponse(
                academicYear.getId(),
                academicYear.getName(),
                academicYear.getStartDate(),
                academicYear.getEndDate(),
                academicYear.getStatus(),
                academicYear.getVoidReason(),
                academicYear.getVoidedAt(),
                academicYear.getVoidedByUserId(),
                academicYear.getVersion(),
                academicYear.getCreatedAt(),
                academicYear.getUpdatedAt()
        );
    }
}