package com.dawnrise.identity.platform.auth.passwordreset.controller;

import com.dawnrise.identity.auth.passwordreset.dto.CompletePasswordResetRequest;
import com.dawnrise.identity.auth.passwordreset.dto.PasswordResetTokenValidationResponse;
import com.dawnrise.identity.platform.auth.passwordreset.dto.PlatformPasswordResetRequest;
import com.dawnrise.identity.platform.auth.passwordreset.service.PlatformPasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/auth/password-reset")
public class PlatformPasswordResetController {
    private final PlatformPasswordResetService service;
    public PlatformPasswordResetController(PlatformPasswordResetService service) { this.service = service; }
    @PostMapping("/request") public ResponseEntity<Void> request(@Valid @RequestBody PlatformPasswordResetRequest request) {
        service.requestReset(request); return ResponseEntity.accepted().build();
    }
    @GetMapping("/validate") public ResponseEntity<PasswordResetTokenValidationResponse> validate(@RequestParam String token) {
        return ResponseEntity.ok(new PasswordResetTokenValidationResponse(service.isTokenValid(token)));
    }
    @PostMapping("/complete") public ResponseEntity<Void> complete(@Valid @RequestBody CompletePasswordResetRequest request) {
        service.completeReset(request); return ResponseEntity.noContent().build();
    }
}
