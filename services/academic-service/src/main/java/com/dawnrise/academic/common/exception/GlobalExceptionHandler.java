package com.dawnrise.academic.common.exception;

import com.dawnrise.academic.academicyear.exception.AcademicYearConflictException;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.exception.InvalidAcademicYearException;
import com.dawnrise.academic.gradelevel.exception.GradeLevelConflictException;
import com.dawnrise.academic.gradelevel.exception.GradeLevelNotFoundException;
import com.dawnrise.academic.gradelevel.exception.InvalidGradeLevelException;
import com.dawnrise.academic.section.exception.InvalidSectionException;
import com.dawnrise.academic.section.exception.SectionConflictException;
import com.dawnrise.academic.section.exception.SectionNotFoundException;
import com.dawnrise.academic.subject.exception.InvalidSubjectException;
import com.dawnrise.academic.subject.exception.SubjectConflictException;
import com.dawnrise.academic.subject.exception.SubjectNotFoundException;
import com.dawnrise.academic.gradelevelsubject.exception.GradeLevelSubjectConflictException;
import com.dawnrise.academic.gradelevelsubject.exception.GradeLevelSubjectNotFoundException;
import com.dawnrise.academic.gradelevelsubject.exception.InvalidGradeLevelSubjectException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
            ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<ApiErrorResponse> handleDatabaseConflict(
            RuntimeException exception,
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