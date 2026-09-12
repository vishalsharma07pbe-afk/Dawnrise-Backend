package com.dawnrise.academic.common.exception;

import com.dawnrise.academic.academicyear.exception.AcademicYearConflictException;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.exception.InvalidAcademicYearException;
import com.dawnrise.academic.academicyearrollover.exception.InvalidRolloverRequestException;
import com.dawnrise.academic.academicyearrollover.exception.RolloverConflictException;
import com.dawnrise.academic.academicyearrollover.exception.RolloverOperationNotFoundException;
import com.dawnrise.academic.gradelevel.exception.GradeLevelConflictException;
import com.dawnrise.academic.gradelevel.exception.GradeLevelNotFoundException;
import com.dawnrise.academic.gradelevel.exception.InvalidGradeLevelException;
import com.dawnrise.academic.section.exception.InvalidSectionException;
import com.dawnrise.academic.section.exception.SectionConflictException;
import com.dawnrise.academic.section.exception.SectionNotFoundException;
import com.dawnrise.academic.security.InvalidAuthenticatedAcademicActorException;
import com.dawnrise.academic.subject.exception.InvalidSubjectException;
import com.dawnrise.academic.subject.exception.SubjectConflictException;
import com.dawnrise.academic.subject.exception.SubjectNotFoundException;
import com.dawnrise.academic.gradelevelsubject.exception.GradeLevelSubjectConflictException;
import com.dawnrise.academic.gradelevelsubject.exception.GradeLevelSubjectNotFoundException;
import com.dawnrise.academic.gradelevelsubject.exception.InvalidGradeLevelSubjectException;
import com.dawnrise.academic.teacherassignment.exception.InvalidTeacherAssignmentException;
import com.dawnrise.academic.teacherassignment.exception.TeacherAssignmentConflictException;
import com.dawnrise.academic.teacherassignment.exception.TeacherAssignmentNotFoundException;
import com.dawnrise.academic.teacherassignment.exception.TeacherNotEligibleException;
import com.dawnrise.academic.studentenrollment.integration.identity.IdentityStudentEligibilityException;
import com.dawnrise.academic.studentenrollment.exception.InvalidStudentEnrollmentException;
import com.dawnrise.academic.studentenrollment.exception.StudentEnrollmentConflictException;
import com.dawnrise.academic.studentenrollment.exception.StudentEnrollmentNotFoundException;
import com.dawnrise.academic.studentenrollment.exception.StudentNotEligibleException;
import com.dawnrise.academic.studentprogression.exception.InvalidStudentProgressionException;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionConflictException;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionOperationNotFoundException;
import com.dawnrise.academic.academiccalendar.exception.AcademicCalendarConflictException;
import com.dawnrise.academic.academiccalendar.exception.AcademicCalendarDayNotFoundException;
import com.dawnrise.academic.academiccalendar.exception.InvalidAcademicCalendarException;
import com.dawnrise.academic.studentattendance.policy.exception.InvalidStudentAttendancePolicyException;
import com.dawnrise.academic.studentattendance.policy.exception.StudentAttendancePolicyConflictException;
import com.dawnrise.academic.studentattendance.policy.exception.StudentAttendancePolicyNotFoundException;
import com.dawnrise.academic.studentattendance.recording.exception.InvalidStudentAttendanceRecordingException;
import com.dawnrise.academic.studentattendance.recording.exception.StudentAttendanceRecordingConflictException;
import com.dawnrise.academic.studentattendance.recording.exception.StudentAttendanceSessionNotFoundException;
import com.dawnrise.academic.common.integration.school.SchoolTimeZoneUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.dawnrise.academic.teacherassignment.integration.identity.IdentityTeacherEligibilityException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidAcademicYearException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidAcademicYear(
            InvalidAcademicYearException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(InvalidRolloverRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRolloverRequest(
            InvalidRolloverRequestException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(RolloverOperationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleRolloverOperationNotFound(
            RolloverOperationNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(RolloverConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleRolloverConflict(
            RolloverConflictException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(AcademicYearNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAcademicYearNotFound(
            AcademicYearNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(AcademicYearConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleAcademicYearConflict(
            AcademicYearConflictException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(fieldError ->
                        validationErrors.putIfAbsent(
                                fieldError.getField(),
                                fieldError.getDefaultMessage()
                        )
                );

        return response(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request,
                validationErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new LinkedHashMap<>();

        exception.getConstraintViolations()
                .forEach(violation ->
                        validationErrors.putIfAbsent(
                                violation.getPropertyPath().toString(),
                                violation.getMessage()
                        )
                );

        return response(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request,
                validationErrors
        );
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "Request contains an invalid value",
                request,
                null
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        LOGGER.warn(
                "Unsupported request method for {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        ResponseEntity<ApiErrorResponse> entity = response(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Request method is not supported",
                request,
                null
        );

        HttpHeaders headers = new HttpHeaders();
        String[] supportedMethods = exception.getSupportedMethods();
        if (supportedMethods != null && supportedMethods.length > 0) {
            headers.setAllow(exception.getSupportedHttpMethods());
        }

        return new ResponseEntity<>(
                entity.getBody(),
                headers,
                HttpStatus.METHOD_NOT_ALLOWED
        );
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthorizationDenied(
            AuthorizationDeniedException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                request,
                null
        );
    }

    @ExceptionHandler({
            DataIntegrityViolationException.class,
            OptimisticLockingFailureException.class,
            OptimisticLockException.class
    })
    public ResponseEntity<ApiErrorResponse> handlePersistenceConflict(
            Exception exception,
            HttpServletRequest request
    ) {
        LOGGER.warn(
                "Academic data conflict for {} {}",
                request.getMethod(),
                request.getRequestURI()
        );

        return response(
                HttpStatus.CONFLICT,
                "Academic data conflicts with existing or newer data",
                request,
                null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        LOGGER.error(
                "Unhandled error for {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.",
                request,
                null
        );
    }

    @ExceptionHandler({
            InvalidAuthenticatedAcademicActorException.class,
            InvalidAcademicCalendarException.class,
            InvalidStudentAttendancePolicyException.class,
            InvalidStudentAttendanceRecordingException.class
    })
    public ResponseEntity<ApiErrorResponse> handleAttendanceBadRequest(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler({
            AcademicCalendarConflictException.class,
            StudentAttendancePolicyConflictException.class,
            StudentAttendanceRecordingConflictException.class
    })
    public ResponseEntity<ApiErrorResponse> handleAttendanceConflict(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler({
            AcademicCalendarDayNotFoundException.class,
            StudentAttendancePolicyNotFoundException.class,
            StudentAttendanceSessionNotFoundException.class
    })
    public ResponseEntity<ApiErrorResponse> handleAttendanceNotFound(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(InvalidGradeLevelException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidGradeLevel(
            InvalidGradeLevelException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(GradeLevelNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleGradeLevelNotFound(
            GradeLevelNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(GradeLevelConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleGradeLevelConflict(
            GradeLevelConflictException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(InvalidSectionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidSection(
            InvalidSectionException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(SectionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleSectionNotFound(
            SectionNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(SectionConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleSectionConflict(
            SectionConflictException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(InvalidSubjectException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidSubject(
            InvalidSubjectException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(SubjectNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleSubjectNotFound(
            SubjectNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(SubjectConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleSubjectConflict(
            SubjectConflictException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(InvalidGradeLevelSubjectException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidGradeLevelSubject(
            InvalidGradeLevelSubjectException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(GradeLevelSubjectNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleGradeLevelSubjectNotFound(
            GradeLevelSubjectNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(GradeLevelSubjectConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleGradeLevelSubjectConflict(
            GradeLevelSubjectConflictException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(IdentityTeacherEligibilityException.class)
    public ResponseEntity<ApiErrorResponse> handleIdentityEligibilityFailure(
            IdentityTeacherEligibilityException exception,
            HttpServletRequest request
    ) {
        LOGGER.error(
                "Teacher eligibility verification failed for {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Teacher eligibility could not be verified",
                request,
                null
        );
    }

    @ExceptionHandler(SchoolTimeZoneUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleSchoolTimeZoneUnavailable(
            SchoolTimeZoneUnavailableException exception,
            HttpServletRequest request
    ) {
        LOGGER.error(
                "School timezone verification failed for {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "School timezone could not be verified",
                request,
                null
        );
    }

    @ExceptionHandler(InvalidTeacherAssignmentException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTeacherAssignment(
            InvalidTeacherAssignmentException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(TeacherAssignmentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTeacherAssignmentNotFound(
            TeacherAssignmentNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler({
            TeacherAssignmentConflictException.class,
            TeacherNotEligibleException.class
    })
    public ResponseEntity<ApiErrorResponse> handleTeacherAssignmentConflict(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(IdentityStudentEligibilityException.class)
    public ResponseEntity<ApiErrorResponse> handleStudentEligibilityFailure(
            IdentityStudentEligibilityException exception,
            HttpServletRequest request
    ) {
        LOGGER.error(
                "Student eligibility verification failed for {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Student enrollment eligibility could not be verified",
                request,
                null
        );
    }

    @ExceptionHandler(InvalidStudentEnrollmentException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidStudentEnrollment(
            InvalidStudentEnrollmentException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(StudentEnrollmentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleStudentEnrollmentNotFound(
            StudentEnrollmentNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler({
            StudentEnrollmentConflictException.class,
            StudentNotEligibleException.class
    })
    public ResponseEntity<ApiErrorResponse> handleStudentEnrollmentConflict(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(InvalidStudentProgressionException.class)
    public ResponseEntity<ApiErrorResponse>
    handleInvalidStudentProgression(
            InvalidStudentProgressionException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(StudentProgressionOperationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse>
    handleStudentProgressionOperationNotFound(
            StudentProgressionOperationNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(StudentProgressionConflictException.class)
    public ResponseEntity<ApiErrorResponse>
    handleStudentProgressionConflict(
            StudentProgressionConflictException exception,
            HttpServletRequest request
    ) {
        Map<String, String> conflictDetails =
                new LinkedHashMap<>();

        conflictDetails.put(
                "operationStatus",
                exception.getOperationStatus().name()
        );

        conflictDetails.put(
                "failureCode",
                exception.getFailureCode()
        );

        return response(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request,
                conflictDetails
        );
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                validationErrors
        );

        return ResponseEntity
                .status(status)
                .body(body);
    }
}
