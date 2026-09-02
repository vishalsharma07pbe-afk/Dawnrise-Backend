package com.dawnrise.academic.section.mapper;

import com.dawnrise.academic.section.dto.SectionResponse;
import com.dawnrise.academic.section.entity.Section;
import org.springframework.stereotype.Component;

@Component
public class SectionMapper {

    public SectionResponse toResponse(Section section) {
        return new SectionResponse(
                section.getId(),
                section.getAcademicYearId(),
                section.getGradeLevelId(),
                section.getCode(),
                section.getName(),
                section.getDisplayOrder(),
                section.getVersion(),
                section.getCreatedAt(),
                section.getUpdatedAt()
        );
    }
}