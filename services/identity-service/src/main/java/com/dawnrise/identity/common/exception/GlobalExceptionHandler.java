package com.dawnrise.identity.common.exception;

import com.dawnrise.identity.auth.exception.AccountLockedException;
import com.dawnrise.identity.auth.exception.AccountNotActiveException;
import com.dawnrise.identity.auth.exception.InvalidCredentialsException;
import com.dawnrise.identity.auth.exception.PasswordChangeNotAllowedException;
import com.dawnrise.identity.roleapproval.exception.ApprovalNotAllowedException;
import com.dawnrise.identity.roleapproval.exception.InvalidApprovalStateException;
import com.dawnrise.identity.roleremoval.exception.ProtectedRoleRemovalException;
import com.dawnrise.identity.user.exception.InvalidUserStatusTransitionException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.dawnrise.identity.roleapproval.exception.InvalidRoleRequestException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authorization.AuthorizationDeniedException;
import com.dawnrise.identity.auth.activation.exception.InvalidActivationTokenException;
import com.dawnrise.identity.auth.activation.exception.PasswordMismatchException;
import com.dawnrise.identity.auth.passwordreset.exception.InvalidPasswordResetTokenException;
import com.dawnrise.identity.auth.refreshtoken.exception.InvalidRefreshTokenException;
import com.dawnrise.identity.organization.provisioning.exception.ProvisioningConflictException;
import com.dawnrise.identity.organization.provisioning.exception.ProvisioningInProgressException;
import com.dawnrise.identity.profilechange.exception.InvalidProfileChangeStateException;
import com.dawnrise.identity.profilechange.exception.ProfileChangeNotAllowedException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
            ProvisioningConflictException.class,
            ProvisioningInProgressException.class
    })
    public ResponseEntity<ApiErrorResponse>
    handleProvisioningConflict(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        Map<String, String> validationErrors = null;
        String message = exception.getMessage();

        if (exception instanceof ProvisioningConflictException conflict
                && conflict.hasField()) {
            validationErrors = Map.of(
                    conflict.getField(),
                    conflict.getMessage()
            );

            message = "Provisioning request conflicts with existing data";
        }

        ApiErrorResponse response = createErrorResponse(
                HttpStatus.CONFLICT,
                message,
                request.getRequestURI(),
                validationErrors
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateResource(
            DuplicateResourceException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
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

        ApiErrorResponse response = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request.getRequestURI(),
                validationErrors
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentialsException(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ){
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.UNAUTHORIZED,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(response);
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountNotActiveException(
            AccountNotActiveException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.FORBIDDEN,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountLockedException(
            AccountLockedException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.LOCKED,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.LOCKED)
                .body(response);
    }

    @ExceptionHandler(InvalidRoleRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRoleRequestException(
            InvalidRoleRequestException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(ApprovalNotAllowedException.class)
    public ResponseEntity<ApiErrorResponse> handleApprovalNotAllowedException(
            ApprovalNotAllowedException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.FORBIDDEN,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }

    @ExceptionHandler(InvalidApprovalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidApprovalStateException(
            InvalidApprovalStateException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(ProtectedRoleRemovalException.class)
    public ResponseEntity<ApiErrorResponse> handleProtectedRoleRemoval(
            ProtectedRoleRemovalException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.CONFLICT,
                "This request was modified by another user. Please refresh and try again.",
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler({
            InvalidActivationTokenException.class,
            InvalidPasswordResetTokenException.class,
            PasswordMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleActivationValidationException(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRefreshToken(
            InvalidRefreshTokenException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.UNAUTHORIZED,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(response);
    }

    @ExceptionHandler(PasswordChangeNotAllowedException.class)
    public ResponseEntity<ApiErrorResponse> handlePasswordChangeNotAllowed(
            PasswordChangeNotAllowedException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(InvalidUserStatusTransitionException.class)
    public ResponseEntity<ApiErrorResponse>
    handleInvalidUserStatusTransition(
            InvalidUserStatusTransitionException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthorizationDenied(
            AuthorizationDeniedException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        LOGGER.error(
                "Unhandled error for {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        ApiErrorResponse response = createErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.",
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    @ExceptionHandler(ProfileChangeNotAllowedException.class)
    public ResponseEntity<ApiErrorResponse> handleProfileChangeNotAllowed(
            ProfileChangeNotAllowedException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.FORBIDDEN,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }

    @ExceptionHandler(InvalidProfileChangeStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidProfileChangeState(
            InvalidProfileChangeStateException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    private ApiErrorResponse createErrorResponse(
            HttpStatus status,
            String message,
            String path,
            Map<String, String> validationErrors
    ) {
        return new ApiErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                validationErrors
        );
    }

}
