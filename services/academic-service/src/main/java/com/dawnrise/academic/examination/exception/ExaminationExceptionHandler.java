package com.dawnrise.academic.examination.exception;

import com.dawnrise.academic.common.exception.ApiErrorResponse;
import com.dawnrise.academic.examination.controller.ExaminationController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

@RestControllerAdvice(assignableTypes = ExaminationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ExaminationExceptionHandler {
    @ExceptionHandler(InvalidExaminationException.class)
    public ResponseEntity<ApiErrorResponse> invalid(
            InvalidExaminationException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(ExaminationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> notFound(
            ExaminationNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(ExaminationConflictException.class)
    public ResponseEntity<ApiErrorResponse> conflict(
            ExaminationConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status,
            String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                OffsetDateTime.now(), status.value(), status.getReasonPhrase(),
                message, request.getRequestURI(), null));
    }
}
