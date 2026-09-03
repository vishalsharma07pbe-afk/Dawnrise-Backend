package com.dawnrise.identity.user.eligibility.service.impl;

import com.dawnrise.identity.user.eligibility.dto.StudentEnrollmentEligibilityResponse;
import com.dawnrise.identity.user.eligibility.enums.StudentEnrollmentEligibilityReason;
import com.dawnrise.identity.user.eligibility.service.StudentEnrollmentEligibilityService;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class StudentEnrollmentEligibilityServiceImpl
        implements StudentEnrollmentEligibilityService {

    private static final Set<UserStatus> ENROLLABLE_STATUSES =
            Set.of(
                    UserStatus.ACTIVE,
                    UserStatus.PENDING_ACTIVATION,
                    UserStatus.LOCKED
            );

    private final UserRepository userRepository;

    public StudentEnrollmentEligibilityServiceImpl(
            UserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    @Override
    public StudentEnrollmentEligibilityResponse check(
            long organizationId,
            long userId
    ) {
        Optional<User> optionalUser =
                userRepository.findByOrganizationIdAndId(
                        organizationId,
                        userId
                );

        if (optionalUser.isEmpty()) {
            return new StudentEnrollmentEligibilityResponse(
                    userId,
                    organizationId,
                    null,
                    false,
                    StudentEnrollmentEligibilityReason.USER_NOT_FOUND
            );
        }

        User user = optionalUser.get();
        String displayName = displayName(user);

        if (!ENROLLABLE_STATUSES.contains(user.getStatus())) {
            return new StudentEnrollmentEligibilityResponse(
                    user.getId(),
                    user.getOrganizationId(),
                    displayName,
                    false,
                    StudentEnrollmentEligibilityReason.USER_NOT_ENROLLABLE
            );
        }

        if (!user.getRoles().contains(UserRole.STUDENT)) {
            return new StudentEnrollmentEligibilityResponse(
                    user.getId(),
                    user.getOrganizationId(),
                    displayName,
                    false,
                    StudentEnrollmentEligibilityReason.STUDENT_ROLE_REQUIRED
            );
        }

        return new StudentEnrollmentEligibilityResponse(
                user.getId(),
                user.getOrganizationId(),
                displayName,
                true,
                StudentEnrollmentEligibilityReason.ELIGIBLE
        );
    }

    @Override
    public List<StudentEnrollmentEligibilityResponse> checkBatch(
            long organizationId,
            List<Long> userIds
    ) {
        Map<Long, User> usersById = userRepository
                .findAllByOrganizationIdAndIdIn(
                        organizationId,
                        userIds
                )
                .stream()
                .collect(Collectors.toMap(
                        User::getId,
                        Function.identity()
                ));

        return userIds.stream()
                .map(userId -> eligibilityFor(
                        organizationId,
                        userId,
                        Optional.ofNullable(usersById.get(userId))
                ))
                .toList();
    }

    private StudentEnrollmentEligibilityResponse eligibilityFor(
            long organizationId,
            long userId,
            Optional<User> optionalUser
    ) {
        if (optionalUser.isEmpty()) {
            return new StudentEnrollmentEligibilityResponse(
                    userId,
                    organizationId,
                    null,
                    false,
                    StudentEnrollmentEligibilityReason.USER_NOT_FOUND
            );
        }

        User user = optionalUser.get();
        String displayName = displayName(user);

        if (!ENROLLABLE_STATUSES.contains(user.getStatus())) {
            return new StudentEnrollmentEligibilityResponse(
                    user.getId(),
                    user.getOrganizationId(),
                    displayName,
                    false,
                    StudentEnrollmentEligibilityReason.USER_NOT_ENROLLABLE
            );
        }

        if (!user.getRoles().contains(UserRole.STUDENT)) {
            return new StudentEnrollmentEligibilityResponse(
                    user.getId(),
                    user.getOrganizationId(),
                    displayName,
                    false,
                    StudentEnrollmentEligibilityReason.STUDENT_ROLE_REQUIRED
            );
        }

        return new StudentEnrollmentEligibilityResponse(
                user.getId(),
                user.getOrganizationId(),
                displayName,
                true,
                StudentEnrollmentEligibilityReason.ELIGIBLE
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
                .orElse("Student " + user.getId());
    }
}
