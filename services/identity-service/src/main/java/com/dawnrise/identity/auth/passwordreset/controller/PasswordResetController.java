package com.dawnrise.identity.auth.passwordreset.controller;

import com.dawnrise.identity.auth.passwordreset.dto.CompletePasswordResetRequest;
import com.dawnrise.identity.auth.passwordreset.dto.PasswordResetRequest;
import com.dawnrise.identity.auth.passwordreset.dto.PasswordResetTokenValidationResponse;
import com.dawnrise.identity.auth.passwordreset.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(
            PasswordResetService passwordResetService
    ) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/request")
    public ResponseEntity<Void> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        passwordResetService.requestPasswordReset(request);

        return ResponseEntity.accepted().build();
    }

    @GetMapping("/validate")
    public ResponseEntity<PasswordResetTokenValidationResponse>
    validatePasswordResetToken(@RequestParam String token) {
        boolean valid =
                passwordResetService.isPasswordResetTokenValid(token);

        return ResponseEntity.ok(
                new PasswordResetTokenValidationResponse(valid)
        );
    }

    @PostMapping("/complete")
    public ResponseEntity<Void> completePasswordReset(
            @Valid @RequestBody CompletePasswordResetRequest request
    ) {
        passwordResetService.completePasswordReset(request);

        return ResponseEntity.noContent().build();
    }
}
