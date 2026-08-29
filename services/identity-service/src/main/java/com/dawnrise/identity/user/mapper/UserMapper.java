package com.dawnrise.identity.user.mapper;

import com.dawnrise.identity.user.dto.CreateUserRequest;
import com.dawnrise.identity.user.dto.UpdateUserProfileRequest;
import com.dawnrise.identity.user.dto.UserResponse;
import com.dawnrise.identity.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(CreateUserRequest request) {
        User user = new User();

        user.setOrganizationId(request.getOrganizationId());
        user.setUsername(request.getUsername());

        user.setFirstName(request.getFirstName());
        user.setMiddleName(request.getMiddleName());
        user.setLastName(request.getLastName());

        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRoles(request.getRoles());

        return user;
    }

    public void updateProfile(
            UpdateUserProfileRequest request,
            User user
    ) {
        user.setFirstName(request.getFirstName());
        user.setMiddleName(request.getMiddleName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getOrganizationId(),
                user.getUsername(),
                user.getFirstName(),
                user.getMiddleName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getRoles(),
                user.getStatus(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}