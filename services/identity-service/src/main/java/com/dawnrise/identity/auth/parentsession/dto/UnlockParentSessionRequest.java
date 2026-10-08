package com.dawnrise.identity.auth.parentsession.dto;
import jakarta.validation.constraints.NotBlank;
public record UnlockParentSessionRequest(@NotBlank String password) {}
