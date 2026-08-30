package com.dawnrise.school.school.DTO;

public record SchoolBrandingResponse(
        Long id,
        String name,
        String motto,
        String tagline,
        boolean hasLogo
) {}
