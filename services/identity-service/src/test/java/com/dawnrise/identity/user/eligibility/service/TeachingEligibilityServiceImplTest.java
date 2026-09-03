package com.dawnrise.identity.user.eligibility.service;

import com.dawnrise.identity.user.eligibility.dto.TeachingEligibilityResponse;
import com.dawnrise.identity.user.eligibility.enums.TeachingEligibilityReason;
import com.dawnrise.identity.user.eligibility.service.impl.TeachingEligibilityServiceImpl;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeachingEligibilityServiceImplTest {

    private static final long ORGANIZATION_ID = 10L;
    private static final long USER_ID = 20L;

    @Mock
    private UserRepository userRepository;

    private TeachingEligibilityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TeachingEligibilityServiceImpl(userRepository);
    }

    @Test
    void activeTeacherIsEligible() {
        when(userRepository.findByOrganizationIdAndId(
                ORGANIZATION_ID,
                USER_ID
        )).thenReturn(Optional.of(user(
                ORGANIZATION_ID,
                USER_ID,
                UserStatus.ACTIVE,
                Set.of(UserRole.TEACHER)
        )));

        TeachingEligibilityResponse response =
                service.check(ORGANIZATION_ID, USER_ID);

        assertTrue(response.eligible());
        assertEquals(TeachingEligibilityReason.ELIGIBLE, response.reason());
        assertEquals("Anita Devi Sharma", response.displayName());
    }

    @Test
    void activeUserWithoutTeacherRoleRequiresTeacherRole() {
        when(userRepository.findByOrganizationIdAndId(
                ORGANIZATION_ID,
                USER_ID
        )).thenReturn(Optional.of(user(
                ORGANIZATION_ID,
                USER_ID,
                UserStatus.ACTIVE,
                Set.of(UserRole.HR)
        )));

        TeachingEligibilityResponse response =
                service.check(ORGANIZATION_ID, USER_ID);

        assertFalse(response.eligible());
        assertEquals(
                TeachingEligibilityReason.TEACHER_ROLE_REQUIRED,
                response.reason()
        );
    }

    @Test
    void nonActiveUsersAreNotEligible() {
        for (UserStatus status : Set.of(
                UserStatus.INACTIVE,
                UserStatus.SUSPENDED,
                UserStatus.PENDING_APPROVAL,
                UserStatus.PENDING_ACTIVATION,
                UserStatus.LOCKED
        )) {
            when(userRepository.findByOrganizationIdAndId(
                    ORGANIZATION_ID,
                    USER_ID
            )).thenReturn(Optional.of(user(
                    ORGANIZATION_ID,
                    USER_ID,
                    status,
                    Set.of(UserRole.TEACHER)
            )));

            TeachingEligibilityResponse response =
                    service.check(ORGANIZATION_ID, USER_ID);

            assertFalse(response.eligible());
            assertEquals(
                    TeachingEligibilityReason.USER_NOT_ACTIVE,
                    response.reason()
            );
        }
    }

    @Test
    void missingUserReturnsNotFoundWithoutUserData() {
        when(userRepository.findByOrganizationIdAndId(
                ORGANIZATION_ID,
                USER_ID
        )).thenReturn(Optional.empty());

        TeachingEligibilityResponse response =
                service.check(ORGANIZATION_ID, USER_ID);

        assertFalse(response.eligible());
        assertEquals(
                TeachingEligibilityReason.USER_NOT_FOUND,
                response.reason()
        );
        assertEquals(USER_ID, response.userId());
        assertEquals(ORGANIZATION_ID, response.organizationId());
        assertNull(response.displayName());
    }

    @Test
    void userFromAnotherOrganizationReturnsNotFound() {
        when(userRepository.findByOrganizationIdAndId(
                ORGANIZATION_ID,
                USER_ID
        )).thenReturn(Optional.empty());

        TeachingEligibilityResponse response =
                service.check(ORGANIZATION_ID, USER_ID);

        assertEquals(
                TeachingEligibilityReason.USER_NOT_FOUND,
                response.reason()
        );
        verify(userRepository).findByOrganizationIdAndId(
                ORGANIZATION_ID,
                USER_ID
        );
    }

    @Test
    void displayNameHandlesNamesSafely() {
        User fullName = user(
                ORGANIZATION_ID,
                USER_ID,
                UserStatus.ACTIVE,
                Set.of(UserRole.TEACHER)
        );
        fullName.setFirstName(" Anita ");
        fullName.setMiddleName("  Devi ");
        fullName.setLastName(" Sharma ");
        when(userRepository.findByOrganizationIdAndId(
                ORGANIZATION_ID,
                USER_ID
        )).thenReturn(Optional.of(fullName));

        assertEquals(
                "Anita Devi Sharma",
                service.check(ORGANIZATION_ID, USER_ID).displayName()
        );

        User partialName = user(
                ORGANIZATION_ID,
                USER_ID,
                UserStatus.ACTIVE,
                Set.of(UserRole.TEACHER)
        );
        partialName.setFirstName(" ");
        partialName.setMiddleName(null);
        partialName.setLastName(" Rao ");
        when(userRepository.findByOrganizationIdAndId(
                ORGANIZATION_ID,
                USER_ID
        )).thenReturn(Optional.of(partialName));

        assertEquals(
                "Rao",
                service.check(ORGANIZATION_ID, USER_ID).displayName()
        );

        User unnamed = user(
                ORGANIZATION_ID,
                USER_ID,
                UserStatus.ACTIVE,
                Set.of(UserRole.TEACHER)
        );
        unnamed.setFirstName(" ");
        unnamed.setMiddleName(null);
        unnamed.setLastName(" ");
        when(userRepository.findByOrganizationIdAndId(
                ORGANIZATION_ID,
                USER_ID
        )).thenReturn(Optional.of(unnamed));

        assertEquals(
                "Teacher " + USER_ID,
                service.check(ORGANIZATION_ID, USER_ID).displayName()
        );
    }

    private static User user(
            Long organizationId,
            Long userId,
            UserStatus status,
            Set<UserRole> roles
    ) {
        User user = new User(
                organizationId,
                "teacher01",
                "Anita",
                roles
        );
        user.setMiddleName("Devi");
        user.setLastName("Sharma");
        user.setStatus(status);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
