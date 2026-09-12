package com.dawnrise.school.security;

import com.dawnrise.school.school.exception.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

public class InternalServiceErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public InternalServiceErrorResponseWriter(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse body = new ApiErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                null
        );

        objectMapper.writeValue(response.getWriter(), body);
    }
}
