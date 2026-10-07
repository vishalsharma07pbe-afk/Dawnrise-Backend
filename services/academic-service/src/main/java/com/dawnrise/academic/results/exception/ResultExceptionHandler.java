package com.dawnrise.academic.results.exception;

import com.dawnrise.academic.common.exception.ApiErrorResponse;
import com.dawnrise.academic.results.controller.ResultSubjectSheetController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

@RestControllerAdvice(assignableTypes = ResultSubjectSheetController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ResultExceptionHandler {
    @ExceptionHandler(InvalidResultException.class)
    public ResponseEntity<ApiErrorResponse> invalid(
            InvalidResultException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(ResultNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> notFound(
            ResultNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(ResultConflictException.class)
    public ResponseEntity<ApiErrorResponse> conflict(
            ResultConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status,
            String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                OffsetDateTime.now(), status.value(), status.getReasonPhrase(),
                message, request.getRequestURI(), null));
    }
}
