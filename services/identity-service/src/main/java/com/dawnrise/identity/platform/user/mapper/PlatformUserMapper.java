package com.dawnrise.identity.platform.user.mapper;

import com.dawnrise.identity.platform.user.dto.PlatformUserResponse;
import com.dawnrise.identity.platform.user.entity.PlatformUser;
import org.springframework.stereotype.Component;

@Component
public class PlatformUserMapper {

    public PlatformUserResponse toResponse(
            PlatformUser platformUser
    ) {
        if (platformUser == null) {
            return null;
        }

        return new PlatformUserResponse(
                platformUser.getId(),
                platformUser.getUsername(),
                platformUser.getFirstName(),
                platformUser.getMiddleName(),
                platformUser.getLastName(),
                platformUser.getEmail(),
                platformUser.getPhone(),
                platformUser.getRoles(),
                platformUser.getStatus(),
                platformUser.getLastLoginAt(),
                platformUser.getCreatedAt(),
                platformUser.getUpdatedAt()
        );
    }
}