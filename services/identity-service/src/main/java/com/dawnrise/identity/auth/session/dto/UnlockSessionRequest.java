package com.dawnrise.identity.auth.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnlockSessionRequest(
        @NotBlank @Size(max = 72) String password
) {
}
