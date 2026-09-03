package com.dawnrise.identity.user.eligibility.service.impl;

import com.dawnrise.identity.user.eligibility.dto.TeachingEligibilityResponse;
import com.dawnrise.identity.user.eligibility.enums.TeachingEligibilityReason;
import com.dawnrise.identity.user.eligibility.service.TeachingEligibilityService;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class TeachingEligibilityServiceImpl
        implements TeachingEligibilityService {

    private final UserRepository userRepository;

    public TeachingEligibilityServiceImpl(
            UserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    @Override
    public TeachingEligibilityResponse check(
            long organizationId,
            long userId
    ) {
        Optional<User> optionalUser =
                userRepository.findByOrganizationIdAndId(
                        organizationId,
                        userId
                );

        if (optionalUser.isEmpty()) {
            return new TeachingEligibilityResponse(
                    userId,
                    organizationId,
                    null,
                    false,
                    TeachingEligibilityReason.USER_NOT_FOUND
            );
        }

        User user = optionalUser.get();
        String displayName = displayName(user);

        if (user.getStatus() != UserStatus.ACTIVE) {
            return new TeachingEligibilityResponse(
                    user.getId(),
                    user.getOrganizationId(),
                    displayName,
                    false,
                    TeachingEligibilityReason.USER_NOT_ACTIVE
            );
        }

        if (!user.getRoles().contains(UserRole.TEACHER)) {
            return new TeachingEligibilityResponse(
                    user.getId(),
                    user.getOrganizationId(),
                    displayName,
                    false,
                    TeachingEligibilityReason.TEACHER_ROLE_REQUIRED
            );
        }

        return new TeachingEligibilityResponse(
                user.getId(),
                user.getOrganizationId(),
                displayName,
                true,
                TeachingEligibilityReason.ELIGIBLE
        );
    }

    private String displayName(User user) {
        return Stream.of(
                        user.getFirstName(),
                        user.getMiddleName(),
                        user.getLastName()
                )
                .filter(value ->
                        value != null && !value.isBlank()
                )
                .map(String::trim)
                .reduce((left, right) -> left + " " + right)
                .orElse("Teacher " + user.getId());
    }
}