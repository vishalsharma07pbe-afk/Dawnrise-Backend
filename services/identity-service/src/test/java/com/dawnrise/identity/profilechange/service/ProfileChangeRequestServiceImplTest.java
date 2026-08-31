package com.dawnrise.identity.profilechange.service;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.common.exception.DuplicateResourceException;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.permission.enums.PermissionCode;
import com.dawnrise.identity.profilechange.dto.CreateProfileChangeRequest;
import com.dawnrise.identity.profilechange.dto.ProfileChangeDecisionRequest;
import com.dawnrise.identity.profilechange.dto.ProfileChangeRequestResponse;
import com.dawnrise.identity.profilechange.entity.ProfileChangeRequest;
import com.dawnrise.identity.profilechange.enums.ProfileChangeRequestStatus;
import com.dawnrise.identity.profilechange.exception.InvalidProfileChangeStateException;
import com.dawnrise.identity.profilechange.exception.ProfileChangeNotAllowedException;
import com.dawnrise.identity.profilechange.mapper.ProfileChangeRequestMapper;
import com.dawnrise.identity.profilechange.repository.ProfileChangeRequestRepository;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileChangeRequestServiceImplTest {

    @Mock
    private ProfileChangeRequestRepository requestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfileChangeRequestMapper requestMapper;
    @Mock
    private SecurityAuditService auditService;
    @Mock
    private ProfileChangeExpirationService expirationService;

    private ProfileChangeRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProfileChangeRequestServiceImpl(
                requestRepository,
                userRepository,
                requestMapper,
                auditService,
                expirationService
        );
    }

    @Test
    void createRequest_whenMissingPermission_throwsProfileChangeNotAllowedException() {
        CreateProfileChangeRequest request = createRequest();

        assertThrows(
                ProfileChangeNotAllowedException.class,
                () -> service.createRequest(
                        1L,
                        20L,
                        auth(10L, PermissionCode.PROFILE_UPDATE_SELF),
                        request
                )
        );
        verifyNoInteractions(
                userRepository,
                requestRepository,
                requestMapper,
                auditService,
                expirationService
        );
    }

    @Test
    void createRequest_whenSelfRequest_throwsProfileChangeNotAllowedException() {
        CreateProfileChangeRequest request = createRequest();

        ProfileChangeNotAllowedException exception = assertThrows(
                ProfileChangeNotAllowedException.class,
                () -> service.createRequest(
                        1L,
                        10L,
                        auth(10L, PermissionCode.USER_PROFILE_UPDATE),
                        request
                )
        );

        assertEquals(
                "Use self-service profile update to change your own profile",
                exception.getMessage()
        );
    }

    @Test
    void createRequest_whenPendingRequestExists_throwsDuplicateResourceException() {
        CreateProfileChangeRequest request = createRequest();
        User requester = user(10L, "Admin", "admin@dawnrise.com");
        User target = user(20L, "Rahul", "rahul@dawnrise.com");

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(requester));
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(target));
        when(requestRepository.findFirstByOrganizationIdAndTargetUserIdAndStatus(
                1L,
                20L,
                ProfileChangeRequestStatus.PENDING
        )).thenReturn(Optional.of(pendingRequest(UUID.randomUUID())));

        assertThrows(
                DuplicateResourceException.class,
                () -> service.createRequest(
                        1L,
                        20L,
                        auth(10L, PermissionCode.USER_PROFILE_UPDATE),
                        request
                )
        );
        verify(expirationService).expireIfNecessary(
                eq(1L),
                any(UUID.class),
                any(OffsetDateTime.class)
        );
        verify(requestRepository, never()).save(any());
    }

    @Test
    void createRequest_whenValid_savesNormalizedRequestAndReturnsResponse() {
        CreateProfileChangeRequest request = createRequest();
        request.setFirstName(" Rohan ");
        request.setMiddleName(" ");
        request.setEmail(" ROHAN@DAWNRISE.COM ");
        request.setPhone(" +91 9999999999 ");
        request.setReason(" Legal name correction ");
        User requester = user(10L, "Admin", "admin@dawnrise.com");
        User target = user(20L, "Rahul", "rahul@dawnrise.com");
        ProfileChangeRequestResponse response = response(UUID.randomUUID());

        when(userRepository.findByOrganizationIdAndId(1L, 10L))
                .thenReturn(Optional.of(requester));
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(target));
        when(requestRepository.findFirstByOrganizationIdAndTargetUserIdAndStatus(
                1L,
                20L,
                ProfileChangeRequestStatus.PENDING
        )).thenReturn(Optional.empty());
        when(userRepository.existsByOrganizationIdAndEmailIgnoreCase(
                1L,
                "rohan@dawnrise.com"
        )).thenReturn(false);
        when(requestRepository.save(any(ProfileChangeRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findAllByOrganizationIdAndIdIn(anyLong(), anySet()))
                .thenReturn(List.of(requester));
        when(requestMapper.toResponse(any(), eq("Admin"), isNull()))
                .thenReturn(response);

        ProfileChangeRequestResponse actual = service.createRequest(
                1L,
                20L,
                auth(10L, PermissionCode.USER_PROFILE_UPDATE),
                request
        );

        assertSame(response, actual);
        ArgumentCaptor<ProfileChangeRequest> captor =
                ArgumentCaptor.forClass(ProfileChangeRequest.class);
        verify(requestRepository).save(captor.capture());
        ProfileChangeRequest saved = captor.getValue();
        assertEquals("Rohan", saved.getProposedFirstName());
        assertNull(saved.getProposedMiddleName());
        assertEquals("rohan@dawnrise.com", saved.getProposedEmail());
        assertEquals("+91 9999999999", saved.getProposedPhone());
        assertEquals("Legal name correction", saved.getReason());
        assertEquals(ProfileChangeRequestStatus.PENDING, saved.getStatus());
        verify(auditService).record(
                eq(1L),
                eq(10L),
                eq(SecurityAuditAction.PROFILE_CHANGE_REQUEST_CREATE),
                eq(SecurityAuditOutcome.SUCCESS),
                eq("PROFILE_CHANGE_REQUEST"),
                eq(saved.getId()),
                anyMap()
        );
    }

    @Test
    void approveRequest_whenValid_updatesUserAndApprovesRequest() {
        UUID requestId = UUID.randomUUID();
        User target = user(20L, "Rahul", "rahul@dawnrise.com");
        ProfileChangeRequest entity = pendingRequest(requestId);
        ProfileChangeRequestResponse response = response(requestId);

        when(requestRepository.findByOrganizationIdAndPublicId(1L, requestId))
                .thenReturn(Optional.of(entity));
        when(userRepository.findByOrganizationIdAndId(1L, 20L))
                .thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);
        when(requestRepository.save(entity)).thenReturn(entity);
        when(userRepository.findAllByOrganizationIdAndIdIn(anyLong(), anySet()))
                .thenReturn(List.of(target));
        when(requestMapper.toResponse(any(), anyString(), anyString()))
                .thenReturn(response);

        ProfileChangeRequestResponse actual = service.approveRequest(
                1L,
                requestId,
                auth(20L, PermissionCode.PROFILE_UPDATE_SELF),
                new ProfileChangeDecisionRequest("Approved")
        );

        assertSame(response, actual);
        assertEquals("Rohan", target.getFirstName());
        assertEquals("rohan@dawnrise.com", target.getEmail());
        assertEquals(ProfileChangeRequestStatus.APPROVED, entity.getStatus());
        assertEquals(20L, entity.getDecidedByUserId());
        verify(userRepository).save(target);
        verify(requestRepository).save(entity);
        verify(auditService).record(
                eq(1L),
                eq(20L),
                eq(SecurityAuditAction.PROFILE_CHANGE_REQUEST_APPROVE),
                eq(SecurityAuditOutcome.SUCCESS),
                eq("PROFILE_CHANGE_REQUEST"),
                eq(entity.getId()),
                anyMap()
        );
        verify(auditService).record(
                eq(1L),
                eq(20L),
                eq(SecurityAuditAction.PROFILE_CHANGE_FINAL_APPLY),
                eq(SecurityAuditOutcome.SUCCESS),
                eq("USER"),
                eq(target.getId()),
                anyMap()
        );
    }

    @Test
    void approveRequest_whenExpired_throwsInvalidStateException() {
        UUID requestId = UUID.randomUUID();
        ProfileChangeRequest entity = pendingRequest(
                requestId,
                OffsetDateTime.now().minusMinutes(1)
        );

        when(requestRepository.findByOrganizationIdAndPublicId(1L, requestId))
                .thenReturn(Optional.of(entity));
        when(expirationService.expireIfNecessary(
                eq(1L),
                eq(requestId),
                any(OffsetDateTime.class)
        )).thenReturn(true);

        assertThrows(
                InvalidProfileChangeStateException.class,
                () -> service.approveRequest(
                        1L,
                        requestId,
                        auth(20L, PermissionCode.PROFILE_UPDATE_SELF),
                        new ProfileChangeDecisionRequest("Approved")
                )
        );

        assertEquals(ProfileChangeRequestStatus.PENDING, entity.getStatus());
        verify(userRepository, never()).save(any(User.class));
        verify(requestRepository, never()).save(entity);
        verify(expirationService).expireIfNecessary(
                eq(1L),
                eq(requestId),
                any(OffsetDateTime.class)
        );
        verifyNoInteractions(auditService);
    }

    @Test
    void getRequest_whenViewerUnrelated_throwsProfileChangeNotAllowedException() {
        UUID requestId = UUID.randomUUID();
        ProfileChangeRequest entity = pendingRequest(requestId);

        when(requestRepository.findByOrganizationIdAndPublicId(1L, requestId))
                .thenReturn(Optional.of(entity));

        assertThrows(
                ProfileChangeNotAllowedException.class,
                () -> service.getRequest(
                        1L,
                        requestId,
                        auth(99L, PermissionCode.PROFILE_UPDATE_SELF)
                )
        );
    }

    @Test
    void getRequest_whenMissing_throwsResourceNotFoundException() {
        UUID requestId = UUID.randomUUID();

        when(requestRepository.findByOrganizationIdAndPublicId(1L, requestId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.getRequest(
                        1L,
                        requestId,
                        auth(20L, PermissionCode.PROFILE_UPDATE_SELF)
                )
        );
    }

    private static CreateProfileChangeRequest createRequest() {
        CreateProfileChangeRequest request = new CreateProfileChangeRequest();
        request.setFirstName("Rohan");
        request.setLastName("Sharma");
        request.setEmail("rohan@dawnrise.com");
        request.setPhone("+91 9999999999");
        request.setReason("Legal name correction");
        return request;
    }

    private static ProfileChangeRequest pendingRequest(UUID publicId) {
        return pendingRequest(publicId, OffsetDateTime.now().plusDays(1));
    }

    private static ProfileChangeRequest pendingRequest(
            UUID publicId,
            OffsetDateTime expiresAt
    ) {
        ProfileChangeRequest request = new ProfileChangeRequest(
                1L,
                20L,
                10L,
                "Rahul",
                null,
                null,
                "rahul@dawnrise.com",
                null,
                "Rohan",
                null,
                "Sharma",
                "rohan@dawnrise.com",
                "+91 9999999999",
                "Legal name correction",
                expiresAt
        );
        ReflectionTestUtils.setField(request, "publicId", publicId);
        return request;
    }

    private static User user(Long id, String firstName, String email) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setOrganizationId(1L);
        user.setUsername(firstName.toLowerCase());
        user.setFirstName(firstName);
        user.setEmail(email);
        user.setRoles(Set.of(UserRole.TEACHER));
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private static ProfileChangeRequestResponse response(UUID requestId) {
        return new ProfileChangeRequestResponse(
                requestId,
                "Admin",
                null,
                "Rahul",
                null,
                null,
                "rahul@dawnrise.com",
                null,
                "Rohan",
                null,
                "Sharma",
                "rohan@dawnrise.com",
                "+91 9999999999",
                "Legal name correction",
                ProfileChangeRequestStatus.PENDING,
                null,
                null,
                OffsetDateTime.now().plusDays(1),
                null,
                null
        );
    }

    private static AuthorizationContext auth(
            Long userId,
            PermissionCode permission
    ) {
        return new AuthorizationContext(userId, Set.of(permission));
    }
}
