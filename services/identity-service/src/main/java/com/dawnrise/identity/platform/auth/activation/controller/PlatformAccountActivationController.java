package com.dawnrise.identity.platform.auth.activation.controller;

import com.dawnrise.identity.auth.activation.dto.ActivationTokenValidationResponse;
import com.dawnrise.identity.auth.activation.dto.CompleteAccountActivationRequest;
import com.dawnrise.identity.auth.activation.dto.ResendActivationRequest;
import com.dawnrise.identity.platform.auth.activation.service.PlatformAccountActivationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/auth/activation")
public class PlatformAccountActivationController {

    private final PlatformAccountActivationService activationService;

    public PlatformAccountActivationController(
            PlatformAccountActivationService activationService
    ) {
        this.activationService = activationService;
    }

    @GetMapping("/validate")
    public ResponseEntity<ActivationTokenValidationResponse>
    validateActivationToken(
            @RequestParam("token") String token
    ) {
        boolean valid =
                activationService.isActivationTokenValid(token);

        return ResponseEntity.ok(
                new ActivationTokenValidationResponse(valid)
        );
    }

    @PostMapping("/complete")
    public ResponseEntity<Void> completeActivation(
            @Valid
            @RequestBody
            CompleteAccountActivationRequest request
    ) {
        activationService.completeActivation(request);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend")
    public ResponseEntity<Void> resendActivation(
            @Valid
            @RequestBody
            ResendActivationRequest request
    ) {
        activationService.requestActivationResend(request);

        /*
         * Always return the same response so callers cannot determine
         * whether a Dawnrise employee account exists.
         */
        return ResponseEntity.noContent().build();
    }
}