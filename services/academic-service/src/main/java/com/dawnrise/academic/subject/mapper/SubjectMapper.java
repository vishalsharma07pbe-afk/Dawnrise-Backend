package com.dawnrise.academic.subject.mapper;

import com.dawnrise.academic.subject.dto.SubjectResponse;
import com.dawnrise.academic.subject.entity.Subject;
import org.springframework.stereotype.Component;

@Component
public class SubjectMapper {

    public SubjectResponse toResponse(Subject subject) {
        return new SubjectResponse(
                subject.getId(),
                subject.getAcademicYearId(),
                subject.getCode(),
                subject.getName(),
                subject.getDescription(),
                subject.getVersion(),
                subject.getCreatedAt(),
                subject.getUpdatedAt()
        );
    }
}