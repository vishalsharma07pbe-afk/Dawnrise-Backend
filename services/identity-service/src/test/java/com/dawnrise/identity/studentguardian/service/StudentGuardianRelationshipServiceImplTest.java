package com.dawnrise.identity.studentguardian.service;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;
import com.dawnrise.identity.studentguardian.config.StudentGuardianProperties;
import com.dawnrise.identity.studentguardian.dto.CreateStudentGuardianRelationshipRequest;
import com.dawnrise.identity.studentguardian.dto.EndStudentGuardianRelationshipRequest;
import com.dawnrise.identity.studentguardian.entity.StudentGuardianRelationship;
import com.dawnrise.identity.studentguardian.enums.StudentGuardianRelationshipStatus;
import com.dawnrise.identity.studentguardian.enums.StudentGuardianRelationshipType;
import com.dawnrise.identity.studentguardian.exception.InvalidStudentGuardianRelationshipException;
import com.dawnrise.identity.studentguardian.exception.StudentGuardianRelationshipAccessDeniedException;
import com.dawnrise.identity.studentguardian.exception.StudentGuardianRelationshipConflictException;
import com.dawnrise.identity.studentguardian.mapper.StudentGuardianRelationshipMapper;
import com.dawnrise.identity.studentguardian.repository.StudentGuardianRelationshipRepository;
import com.dawnrise.identity.studentguardian.repository.StudentGuardianRelationshipView;
import com.dawnrise.identity.studentguardian.service.impl.StudentGuardianRelationshipServiceImpl;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentGuardianRelationshipServiceImplTest {

    @Mock
    private StudentGuardianRelationshipRepository repository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SecurityAuditService auditService;

    private StudentGuardianProperties properties;
    private StudentGuardianRelationshipServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new StudentGuardianProperties();
        properties.setMaxActiveGuardiansPerStudent(3);

        service = new StudentGuardianRelationshipServiceImpl(
                repository,
                userRepository,
                properties,
                new StudentGuardianRelationshipMapper(),
                auditService
        );
    }

    @Test
    void create_whenFirstGuardian_makesPrimary() {
        User student = user(10L, UserRole.STUDENT, UserStatus.ACTIVE);
        User guardian = user(20L, UserRole.PARENT, UserStatus.ACTIVE);
        CreateStudentGuardianRelationshipRequest request = request(20L);

        when(userRepository.findByOrganizationIdAndIdForUpdate(1L, 10L))
                .thenReturn(Optional.of(student));
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(guardian));
        when(repository.countByOrganizationIdAndStudentUserIdAndStatus(
                1L,
                10L,
                StudentGuardianRelationshipStatus.ACTIVE
        )).thenReturn(0L);
        when(repository.save(any())).thenAnswer(invocation -> {
            StudentGuardianRelationship relationship =
                    invocation.getArgument(0);
            ReflectionTestUtils.setField(relationship, "id", 100L);
            return relationship;
        });
        when(repository.findHistoryViewsByStudent(1L, 10L))
                .thenAnswer(invocation -> List.of(view(
                        savedRelationshipArgument(),
                        student,
                        guardian
                )));

        service.create(1L, 10L, auth(), request);

        ArgumentCaptor<StudentGuardianRelationship> captor =
                ArgumentCaptor.forClass(
                        StudentGuardianRelationship.class
                );
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().isPrimaryGuardian());

        InOrder inOrder = inOrder(repository, auditService);
        inOrder.verify(repository).flush();
        inOrder.verify(auditService).record(
                anyLong(),
                anyLong(),
                any(),
                any(),
                anyString(),
                anyLong(),
                anyMap()
        );
    }

    @Test
    void create_whenConfiguredMaximumReached_throwsConflict() {
        User student = user(10L, UserRole.STUDENT, UserStatus.ACTIVE);
        User guardian = user(20L, UserRole.PARENT, UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndIdForUpdate(1L, 10L))
                .thenReturn(Optional.of(student));
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(guardian));
        when(repository.countByOrganizationIdAndStudentUserIdAndStatus(
                1L,
                10L,
                StudentGuardianRelationshipStatus.ACTIVE
        )).thenReturn(3L);

        assertThrows(
                StudentGuardianRelationshipConflictException.class,
                () -> service.create(1L, 10L, auth(), request(20L))
        );

        verify(repository, never()).save(any());
    }

    @Test
    void create_whenDuplicateActivePair_throwsConflict() {
        User student = user(10L, UserRole.STUDENT, UserStatus.ACTIVE);
        User guardian = user(20L, UserRole.PARENT, UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndIdForUpdate(1L, 10L))
                .thenReturn(Optional.of(student));
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(guardian));
        when(repository.countByOrganizationIdAndStudentUserIdAndStatus(
                anyLong(),
                anyLong(),
                any()
        )).thenReturn(1L);
        when(repository
                .existsByOrganizationIdAndStudentUserIdAndGuardianUserIdAndStatus(
                        1L,
                        10L,
                        20L,
                        StudentGuardianRelationshipStatus.ACTIVE
                )).thenReturn(true);

        assertThrows(
                StudentGuardianRelationshipConflictException.class,
                () -> service.create(1L, 10L, auth(), request(20L))
        );
    }

    @Test
    void create_whenEndedPairExists_allowsNewRelationship() {
        User student = user(10L, UserRole.STUDENT, UserStatus.ACTIVE);
        User guardian = user(20L, UserRole.PARENT, UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndIdForUpdate(1L, 10L))
                .thenReturn(Optional.of(student));
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(guardian));
        when(repository.countByOrganizationIdAndStudentUserIdAndStatus(
                anyLong(),
                anyLong(),
                any()
        )).thenReturn(0L);
        when(repository
                .existsByOrganizationIdAndStudentUserIdAndGuardianUserIdAndStatus(
                        1L,
                        10L,
                        20L,
                        StudentGuardianRelationshipStatus.ACTIVE
                )).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> {
            StudentGuardianRelationship relationship =
                    invocation.getArgument(0);
            ReflectionTestUtils.setField(relationship, "id", 100L);
            return relationship;
        });
        when(repository.findHistoryViewsByStudent(1L, 10L))
                .thenAnswer(invocation -> List.of(view(
                        savedRelationshipArgument(),
                        student,
                        guardian
                )));

        service.create(1L, 10L, auth(), request(20L));

        verify(repository).save(any());
    }

    @Test
    void create_whenRolesInvalid_throwsInvalidRequest() {
        User student = user(10L, UserRole.PARENT, UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndIdForUpdate(1L, 10L))
                .thenReturn(Optional.of(student));

        assertThrows(
                InvalidStudentGuardianRelationshipException.class,
                () -> service.create(1L, 10L, auth(), request(20L))
        );
    }

    @Test
    void create_whenStatusIneligible_throwsInvalidRequest() {
        User student = user(10L, UserRole.STUDENT, UserStatus.SUSPENDED);

        when(userRepository.findByOrganizationIdAndIdForUpdate(1L, 10L))
                .thenReturn(Optional.of(student));

        assertThrows(
                InvalidStudentGuardianRelationshipException.class,
                () -> service.create(1L, 10L, auth(), request(20L))
        );
    }

    @Test
    void create_whenSelfLink_throwsInvalidRequest() {
        User student = user(10L, UserRole.STUDENT, UserStatus.ACTIVE);
        student.setRoles(Set.of(UserRole.STUDENT, UserRole.PARENT));

        when(userRepository.findByOrganizationIdAndIdForUpdate(1L, 10L))
                .thenReturn(Optional.of(student));
        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(student));

        assertThrows(
                InvalidStudentGuardianRelationshipException.class,
                () -> service.create(1L, 10L, auth(), request(10L))
        );
    }

    @Test
    void setPrimary_demotesExistingPrimaryAndPromotesTarget() {
        User student = user(10L, UserRole.STUDENT, UserStatus.ACTIVE);
        User guardian1 = user(20L, UserRole.PARENT, UserStatus.ACTIVE);
        User guardian2 = user(21L, UserRole.PARENT, UserStatus.ACTIVE);
        StudentGuardianRelationship existing =
                relationship(100L, 10L, 20L, true);
        StudentGuardianRelationship target =
                relationship(101L, 10L, 21L, false);

        when(userRepository.findByOrganizationIdAndIdForUpdate(1L, 10L))
                .thenReturn(Optional.of(student));
        when(repository.findByOrganizationIdAndStudentUserIdAndId(
                1L,
                10L,
                101L
        )).thenReturn(Optional.of(target));
        when(repository
                .findAllByOrganizationIdAndStudentUserIdAndStatusOrderByPrimaryGuardianDescStartedAtAscIdAsc(
                        1L,
                        10L,
                        StudentGuardianRelationshipStatus.ACTIVE
                )).thenReturn(List.of(existing, target));
        when(repository.findHistoryViewsByStudent(1L, 10L))
                .thenReturn(List.of(
                        view(existing, student, guardian1),
                        view(target, student, guardian2)
                ));

        service.setPrimary(1L, 10L, 101L, auth());

        assertFalse(existing.isPrimaryGuardian());
        assertTrue(target.isPrimaryGuardian());
        InOrder inOrder = inOrder(repository, auditService);
        inOrder.verify(repository, times(2)).flush();
        inOrder.verify(auditService).record(
                anyLong(),
                anyLong(),
                any(),
                any(),
                anyString(),
                anyLong(),
                anyMap()
        );
    }

    @Test
    void end_whenPrimaryAndOtherActiveWithoutReplacement_throwsConflict() {
        User student = user(10L, UserRole.STUDENT, UserStatus.ACTIVE);
        StudentGuardianRelationship primary =
                relationship(100L, 10L, 20L, true);
        StudentGuardianRelationship other =
                relationship(101L, 10L, 21L, false);

        when(userRepository.findByOrganizationIdAndIdForUpdate(1L, 10L))
                .thenReturn(Optional.of(student));
        when(repository.findByOrganizationIdAndStudentUserIdAndId(
                1L,
                10L,
                100L
        )).thenReturn(Optional.of(primary));
        when(repository
                .findAllByOrganizationIdAndStudentUserIdAndStatusOrderByPrimaryGuardianDescStartedAtAscIdAsc(
                        1L,
                        10L,
                        StudentGuardianRelationshipStatus.ACTIVE
                )).thenReturn(List.of(primary, other));

        assertThrows(
                StudentGuardianRelationshipConflictException.class,
                () -> service.end(
                        1L,
                        10L,
                        100L,
                        auth(),
                        new EndStudentGuardianRelationshipRequest()
                )
        );
    }

    @Test
    void end_whenPrimaryWithReplacement_changesPrimaryAtomically() {
        User student = user(10L, UserRole.STUDENT, UserStatus.ACTIVE);
        User guardian1 = user(20L, UserRole.PARENT, UserStatus.ACTIVE);
        User guardian2 = user(21L, UserRole.PARENT, UserStatus.ACTIVE);
        StudentGuardianRelationship primary =
                relationship(100L, 10L, 20L, true);
        StudentGuardianRelationship replacement =
                relationship(101L, 10L, 21L, false);
        EndStudentGuardianRelationshipRequest request =
                new EndStudentGuardianRelationshipRequest();
        request.setReplacementPrimaryRelationshipId(101L);

        when(userRepository.findByOrganizationIdAndIdForUpdate(1L, 10L))
                .thenReturn(Optional.of(student));
        when(repository.findByOrganizationIdAndStudentUserIdAndId(
                1L,
                10L,
                100L
        )).thenReturn(Optional.of(primary));
        when(repository
                .findAllByOrganizationIdAndStudentUserIdAndStatusOrderByPrimaryGuardianDescStartedAtAscIdAsc(
                        1L,
                        10L,
                        StudentGuardianRelationshipStatus.ACTIVE
                )).thenReturn(List.of(primary, replacement));
        when(repository.findHistoryViewsByStudent(1L, 10L))
                .thenReturn(List.of(
                        view(primary, student, guardian1),
                        view(replacement, student, guardian2)
                ));

        service.end(1L, 10L, 100L, auth(), request);

        assertEquals(
                StudentGuardianRelationshipStatus.ENDED,
                primary.getStatus()
        );
        assertTrue(primary.isPrimaryGuardian());
        assertTrue(replacement.isPrimaryGuardian());
        InOrder inOrder = inOrder(repository, auditService);
        inOrder.verify(repository, times(2)).flush();
        inOrder.verify(auditService).record(
                anyLong(),
                anyLong(),
                any(),
                any(),
                anyString(),
                anyLong(),
                anyMap()
        );
    }

    @Test
    void end_whenNonPrimary_flushesBeforeAudit() {
        User student = user(10L, UserRole.STUDENT, UserStatus.ACTIVE);
        User guardian1 = user(20L, UserRole.PARENT, UserStatus.ACTIVE);
        User guardian2 = user(21L, UserRole.PARENT, UserStatus.ACTIVE);
        StudentGuardianRelationship primary =
                relationship(100L, 10L, 20L, true);
        StudentGuardianRelationship target =
                relationship(101L, 10L, 21L, false);

        when(userRepository.findByOrganizationIdAndIdForUpdate(1L, 10L))
                .thenReturn(Optional.of(student));
        when(repository.findByOrganizationIdAndStudentUserIdAndId(
                1L,
                10L,
                101L
        )).thenReturn(Optional.of(target));
        when(repository
                .findAllByOrganizationIdAndStudentUserIdAndStatusOrderByPrimaryGuardianDescStartedAtAscIdAsc(
                        1L,
                        10L,
                        StudentGuardianRelationshipStatus.ACTIVE
                )).thenReturn(List.of(primary, target));
        when(repository.findHistoryViewsByStudent(1L, 10L))
                .thenReturn(List.of(
                        view(primary, student, guardian1),
                        view(target, student, guardian2)
                ));

        service.end(
                1L,
                10L,
                101L,
                auth(),
                new EndStudentGuardianRelationshipRequest()
        );

        assertEquals(
                StudentGuardianRelationshipStatus.ENDED,
                target.getStatus()
        );

        InOrder inOrder = inOrder(repository, auditService);
        inOrder.verify(repository).flush();
        inOrder.verify(auditService).record(
                anyLong(),
                anyLong(),
                any(),
                any(),
                anyString(),
                anyLong(),
                anyMap()
        );
    }

    @Test
    void end_whenFinalActiveLink_allowsNoReplacement() {
        User student = user(10L, UserRole.STUDENT, UserStatus.ACTIVE);
        User guardian = user(20L, UserRole.PARENT, UserStatus.ACTIVE);
        StudentGuardianRelationship primary =
                relationship(100L, 10L, 20L, true);

        when(userRepository.findByOrganizationIdAndIdForUpdate(1L, 10L))
                .thenReturn(Optional.of(student));
        when(repository.findByOrganizationIdAndStudentUserIdAndId(
                1L,
                10L,
                100L
        )).thenReturn(Optional.of(primary));
        when(repository
                .findAllByOrganizationIdAndStudentUserIdAndStatusOrderByPrimaryGuardianDescStartedAtAscIdAsc(
                        1L,
                        10L,
                        StudentGuardianRelationshipStatus.ACTIVE
                )).thenReturn(List.of(primary));
        when(repository.findHistoryViewsByStudent(1L, 10L))
                .thenReturn(List.of(view(primary, student, guardian)));

        service.end(
                1L,
                10L,
                100L,
                auth(),
                new EndStudentGuardianRelationshipRequest()
        );

        assertEquals(
                StudentGuardianRelationshipStatus.ENDED,
                primary.getStatus()
        );

        InOrder inOrder = inOrder(repository, auditService);
        inOrder.verify(repository).flush();
        inOrder.verify(auditService).record(
                anyLong(),
                anyLong(),
                any(),
                any(),
                anyString(),
                anyLong(),
                anyMap()
        );
    }

    @Test
    void getLinkedStudentsForParent_whenParentRole_returnsActiveLinks() {
        User parent = user(99L, UserRole.PARENT, UserStatus.ACTIVE);
        User student = user(10L, UserRole.STUDENT, UserStatus.ACTIVE);
        StudentGuardianRelationship relationship =
                relationship(100L, 10L, 99L, true);

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(parent));
        when(repository.findActiveViewsByGuardian(
                1L,
                99L,
                StudentGuardianRelationshipStatus.ACTIVE
        )).thenReturn(List.of(view(relationship, student, parent)));

        assertEquals(
                1,
                service.getLinkedStudentsForParent(1L, auth()).size()
        );
    }

    @Test
    void getLinkedStudentsForParent_whenNotParent_throwsForbidden() {
        User teacher = user(99L, UserRole.TEACHER, UserStatus.ACTIVE);

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(teacher));

        assertThrows(
                StudentGuardianRelationshipAccessDeniedException.class,
                () -> service.getLinkedStudentsForParent(1L, auth())
        );
    }

    @Test
    void getLinkedStudentsForParent_whenParentSuspended_throwsForbidden() {
        User parent = user(99L, UserRole.PARENT, UserStatus.SUSPENDED);

        when(userRepository.findByOrganizationIdAndId(1L, 99L))
                .thenReturn(Optional.of(parent));

        assertThrows(
                StudentGuardianRelationshipAccessDeniedException.class,
                () -> service.getLinkedStudentsForParent(1L, auth())
        );
    }

    private CreateStudentGuardianRelationshipRequest request(
            Long guardianUserId
    ) {
        CreateStudentGuardianRelationshipRequest request =
                new CreateStudentGuardianRelationshipRequest();
        request.setGuardianUserId(guardianUserId);
        request.setRelationshipType(StudentGuardianRelationshipType.MOTHER);
        return request;
    }

    private AuthorizationContext auth() {
        return new AuthorizationContext(99L, Set.of());
    }

    private User user(Long id, UserRole role, UserStatus status) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setOrganizationId(1L);
        user.setUsername("user" + id);
        user.setFirstName("User");
        user.setLastName(id.toString());
        user.setRoles(Set.of(role));
        user.setStatus(status);
        return user;
    }

    private StudentGuardianRelationship relationship(
            Long id,
            Long studentId,
            Long guardianId,
            boolean primary
    ) {
        StudentGuardianRelationship relationship =
                new StudentGuardianRelationship(
                        1L,
                        studentId,
                        guardianId,
                        StudentGuardianRelationshipType.GUARDIAN,
                        primary,
                        99L,
                        OffsetDateTime.now()
                );
        ReflectionTestUtils.setField(relationship, "id", id);
        return relationship;
    }

    private StudentGuardianRelationshipView view(
            StudentGuardianRelationship relationship,
            User student,
            User guardian
    ) {
        return new StudentGuardianRelationshipView(
                relationship,
                student,
                guardian
        );
    }

    private StudentGuardianRelationship savedRelationshipArgument() {
        ArgumentCaptor<StudentGuardianRelationship> captor =
                ArgumentCaptor.forClass(
                        StudentGuardianRelationship.class
                );
        verify(repository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
