package com.dawnrise.academic.studentenrollment.dto;

public record StudentTransferResponse(
        StudentEnrollmentResponse previousEnrollment,
        StudentEnrollmentResponse newEnrollment
) {
}