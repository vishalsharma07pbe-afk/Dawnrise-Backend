package com.dawnrise.identity.auth.parentsession.dto;
import java.time.OffsetDateTime;
public record ParentSessionValidationResponse(boolean active, OffsetDateTime authenticatedAt) {}
