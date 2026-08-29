package com.dawnrise.identity.auth.activation.controller;

import com.dawnrise.identity.auth.activation.dto.ActivationTokenValidationResponse;
import com.dawnrise.identity.auth.activation.dto.CompleteAccountActivationRequest;
import com.dawnrise.identity.auth.activation.service.AccountActivationService;
import com.dawnrise.identity.auth.activation.dto.ResendActivationRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/activation")
public class AccountActivationController {

    private final AccountActivationService activationService;

    public AccountActivationController(
            AccountActivationService activationService
    ) {
        this.activationService = activationService;
    }

    @GetMapping("/validate")
    public ResponseEntity<ActivationTokenValidationResponse>
    validateActivationToken(
            @RequestParam String token
    ) {
        boolean valid =
                activationService.isActivationTokenValid(token);

        return ResponseEntity.ok(
                new ActivationTokenValidationResponse(valid)
        );
    }

    @PostMapping("/complete")
    public ResponseEntity<Void> completeActivation(
            @Valid @RequestBody
            CompleteAccountActivationRequest request
    ) {
        activationService.completeActivation(request);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend")
    public ResponseEntity<Void> resendActivationLink(
            @Valid @RequestBody
            ResendActivationRequest request
    ) {
        activationService.requestActivationResend(request);

        return ResponseEntity.accepted().build();
    }
}