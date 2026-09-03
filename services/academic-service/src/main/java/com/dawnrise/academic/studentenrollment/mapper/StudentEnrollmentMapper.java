package com.dawnrise.academic.studentenrollment.mapper;

import com.dawnrise.academic.studentenrollment.dto.StudentEnrollmentResponse;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import org.springframework.stereotype.Component;

@Component
public class StudentEnrollmentMapper {

    public StudentEnrollmentResponse toResponse(
            StudentEnrollment enrollment
    ) {
        return new StudentEnrollmentResponse(
                enrollment.getId(),
                enrollment.getAcademicYearId(),
                enrollment.getGradeLevelId(),
                enrollment.getSectionId(),
                enrollment.getStudentUserId(),
                enrollment.getRollNumber(),
                enrollment.getStatus(),
                enrollment.getEnrolledOn(),
                enrollment.getEndedOn(),
                enrollment.getVersion(),
                enrollment.getCreatedAt(),
                enrollment.getUpdatedAt()
        );
    }
}