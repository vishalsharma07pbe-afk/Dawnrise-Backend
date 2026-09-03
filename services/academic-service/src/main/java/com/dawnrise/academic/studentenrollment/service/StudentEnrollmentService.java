package com.dawnrise.academic.studentenrollment.service;

import com.dawnrise.academic.studentenrollment.dto.CreateStudentEnrollmentRequest;
import com.dawnrise.academic.studentenrollment.dto.BulkCreateStudentEnrollmentsRequest;
import com.dawnrise.academic.studentenrollment.dto.EndStudentEnrollmentRequest;
import com.dawnrise.academic.studentenrollment.dto.StudentEnrollmentResponse;
import com.dawnrise.academic.studentenrollment.dto.StudentTransferResponse;
import com.dawnrise.academic.studentenrollment.dto.TransferStudentEnrollmentRequest;
import com.dawnrise.academic.studentenrollment.dto.UpdateStudentRollNumberRequest;

import java.util.List;

public interface StudentEnrollmentService {

    StudentEnrollmentResponse enroll(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            CreateStudentEnrollmentRequest request
    );

    List<StudentEnrollmentResponse> enrollBulk(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            BulkCreateStudentEnrollmentsRequest request
    );

    StudentEnrollmentResponse getById(
            long organizationId,
            long academicYearId,
            long enrollmentId
    );

    List<StudentEnrollmentResponse> getSectionStudents(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId
    );

    List<StudentEnrollmentResponse> getStudentHistory(
            long organizationId,
            long studentUserId
    );

    StudentEnrollmentResponse updateRollNumber(
            long organizationId,
            long academicYearId,
            long enrollmentId,
            UpdateStudentRollNumberRequest request
    );

    StudentTransferResponse transfer(
            long organizationId,
            long academicYearId,
            long enrollmentId,
            TransferStudentEnrollmentRequest request
    );

    StudentEnrollmentResponse withdraw(
            long organizationId,
            long academicYearId,
            long enrollmentId,
            EndStudentEnrollmentRequest request
    );

    StudentEnrollmentResponse complete(
            long organizationId,
            long academicYearId,
            long enrollmentId,
            EndStudentEnrollmentRequest request
    );
}
