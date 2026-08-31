package com.dawnrise.identity.profilechange.service;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.common.dto.PageResponse;
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
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProfileChangeRequestServiceImpl
        implements ProfileChangeRequestService {

    private static final int REQUEST_EXPIRATION_DAYS = 7;

    private final ProfileChangeRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ProfileChangeRequestMapper requestMapper;
    private final SecurityAuditService auditService;
    private final ProfileChangeExpirationService expirationService;

    public ProfileChangeRequestServiceImpl(
            ProfileChangeRequestRepository requestRepository,
            UserRepository userRepository,
            ProfileChangeRequestMapper requestMapper,
            SecurityAuditService auditService,
            ProfileChangeExpirationService expirationService
    ) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.requestMapper = requestMapper;
        this.auditService = auditService;
        this.expirationService = expirationService;
    }

    @Override
    public ProfileChangeRequestResponse createRequest(
            Long organizationId,
            Long targetUserId,
            AuthorizationContext authorizationContext,
            CreateProfileChangeRequest request
    ) {
        requirePermission(
                authorizationContext,
                PermissionCode.USER_PROFILE_UPDATE
        );

        Long requesterId = authorizationContext.getUserId();

        if (requesterId.equals(targetUserId)) {
            throw new ProfileChangeNotAllowedException(
                    "Use self-service profile update to change your own profile"
            );
        }

        User requester = findUser(organizationId, requesterId);
        User targetUser = findUser(organizationId, targetUserId);

        if (requester.getStatus() != UserStatus.ACTIVE) {
            throw new ProfileChangeNotAllowedException(
                    "Only an active user can request profile changes"
            );
        }

        if (targetUser.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidProfileChangeStateException(
                    "Approval requests can only be created for active users"
            );
        }

        Optional<ProfileChangeRequest> pendingRequest =
                requestRepository
                        .findFirstByOrganizationIdAndTargetUserIdAndStatus(
                                organizationId,
                                targetUserId,
                                ProfileChangeRequestStatus.PENDING
                        );

        if (pendingRequest.isPresent()) {
            boolean expired =
                    expirationService.expireIfNecessary(
                            organizationId,
                            pendingRequest.get().getPublicId(),
                            OffsetDateTime.now()
                    );

            if (!expired) {
                throw new DuplicateResourceException(
                        "This user already has a pending profile change request"
                );
            }
        }

        String proposedFirstName = required(request.getFirstName());
        String proposedMiddleName = optional(request.getMiddleName());
        String proposedLastName = optional(request.getLastName());
        String proposedEmail = email(request.getEmail());
        String proposedPhone = optional(request.getPhone());
        String reason = required(request.getReason());

        ensureEmailAvailable(
                organizationId,
                targetUser,
                proposedEmail
        );

        if (profileMatches(
                targetUser,
                proposedFirstName,
                proposedMiddleName,
                proposedLastName,
                proposedEmail,
                proposedPhone
        )) {
            throw new IllegalArgumentException(
                    "The proposed profile is identical to the current profile"
            );
        }

        OffsetDateTime currentTime = OffsetDateTime.now();

        ProfileChangeRequest profileChangeRequest =
                new ProfileChangeRequest(
                        organizationId,
                        targetUser.getId(),
                        requester.getId(),
                        normalizeRequired(targetUser.getFirstName()),
                        normalizeOptional(targetUser.getMiddleName()),
                        normalizeOptional(targetUser.getLastName()),
                        normalizeEmail(targetUser.getEmail()),
                        normalizeOptional(targetUser.getPhone()),
                        proposedFirstName,
                        proposedMiddleName,
                        proposedLastName,
                        proposedEmail,
                        proposedPhone,
                        reason,
                        currentTime.plusDays(REQUEST_EXPIRATION_DAYS)
                );

        ProfileChangeRequest savedRequest =
                requestRepository.save(profileChangeRequest);

        auditService.record(
                organizationId,
                requester.getId(),
                SecurityAuditAction.PROFILE_CHANGE_REQUEST_CREATE,
                SecurityAuditOutcome.SUCCESS,
                "PROFILE_CHANGE_REQUEST",
                savedRequest.getId(),
                Map.of(
                        "publicRequestId",
                        savedRequest.getPublicId().toString(),
                        "targetUserId",
                        targetUser.getId()
                )
        );

        return toResponse(savedRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileChangeRequestResponse getRequest(
            Long organizationId,
            UUID requestId,
            AuthorizationContext authorizationContext
    ) {
        ProfileChangeRequest request =
                findRequest(organizationId, requestId);

        Long currentUserId = authorizationContext.getUserId();

        if (!currentUserId.equals(request.getTargetUserId())
                && !currentUserId.equals(request.getRequestedByUserId())) {
            throw new ProfileChangeNotAllowedException(
                    "You cannot view this profile change request"
            );
        }

        return toResponse(request);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProfileChangeRequestResponse> getTargetUserRequests(
            Long organizationId,
            Long targetUserId,
            ProfileChangeRequestStatus status,
            AuthorizationContext authorizationContext,
            Pageable pageable
    ) {
        if (!authorizationContext.getUserId().equals(targetUserId)) {
            throw new ProfileChangeNotAllowedException(
                    "You can only view profile requests concerning your account"
            );
        }

        findUser(organizationId, targetUserId);

        Page<ProfileChangeRequest> requests =
                status == null
                        ? requestRepository
                        .findAllByOrganizationIdAndTargetUserId(
                                organizationId,
                                targetUserId,
                                pageable
                        )
                        : requestRepository
                        .findAllByOrganizationIdAndTargetUserIdAndStatus(
                                organizationId,
                                targetUserId,
                                status,
                                pageable
                        );

        Map<Long, String> userNames =
                loadDisplayNames(
                        organizationId,
                        requests.getContent()
                );

        Page<ProfileChangeRequestResponse> responses =
                requests.map(request ->
                        toResponse(request, userNames)
                );

        return PageResponse.from(responses);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProfileChangeRequestResponse> getSubmittedRequests(
            Long organizationId,
            AuthorizationContext authorizationContext,
            Pageable pageable
    ) {
        requirePermission(
                authorizationContext,
                PermissionCode.USER_PROFILE_UPDATE
        );

        Long requesterId = authorizationContext.getUserId();

        findUser(organizationId, requesterId);

        Page<ProfileChangeRequest> requests =
                requestRepository
                        .findAllByOrganizationIdAndRequestedByUserId(
                                organizationId,
                                requesterId,
                                pageable
                        );

        Map<Long, String> userNames =
                loadDisplayNames(
                        organizationId,
                        requests.getContent()
                );

        Page<ProfileChangeRequestResponse> responses =
                requests.map(request ->
                        toResponse(request, userNames)
                );

        return PageResponse.from(responses);
    }

    @Override
    public ProfileChangeRequestResponse approveRequest(
            Long organizationId,
            UUID requestId,
            AuthorizationContext authorizationContext,
            ProfileChangeDecisionRequest decision
    ) {
        requirePermission(
                authorizationContext,
                PermissionCode.PROFILE_UPDATE_SELF
        );

        ProfileChangeRequest request =
                findRequest(organizationId, requestId);

        OffsetDateTime currentTime = OffsetDateTime.now();

        if (expirationService.expireIfNecessary(
                organizationId,
                requestId,
                currentTime
        )) {
            throw new InvalidProfileChangeStateException(
                    "This profile change request has expired"
            );
        }

        requireTargetUser(request, authorizationContext.getUserId());
        ensurePending(request);

        User targetUser = findUser(
                organizationId,
                request.getTargetUserId()
        );

        if (targetUser.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidProfileChangeStateException(
                    "The target account is no longer active"
            );
        }

        ensureOriginalProfileUnchanged(request, targetUser);

        ensureEmailAvailable(
                organizationId,
                targetUser,
                request.getProposedEmail()
        );

        applyProposedProfile(request, targetUser);
        userRepository.save(targetUser);

        request.approve(
                authorizationContext.getUserId(),
                optional(decision.getReason()),
                currentTime
        );

        ProfileChangeRequest savedRequest =
                requestRepository.save(request);

        auditService.record(
                organizationId,
                authorizationContext.getUserId(),
                SecurityAuditAction.PROFILE_CHANGE_REQUEST_APPROVE,
                SecurityAuditOutcome.SUCCESS,
                "PROFILE_CHANGE_REQUEST",
                savedRequest.getId(),
                Map.of(
                        "publicRequestId",
                        savedRequest.getPublicId().toString(),
                        "targetUserId",
                        targetUser.getId()
                )
        );

        auditService.record(
                organizationId,
                authorizationContext.getUserId(),
                SecurityAuditAction.PROFILE_CHANGE_FINAL_APPLY,
                SecurityAuditOutcome.SUCCESS,
                "USER",
                targetUser.getId(),
                Map.of(
                        "publicRequestId",
                        savedRequest.getPublicId().toString()
                )
        );

        return toResponse(savedRequest);
    }

    @Override
    public ProfileChangeRequestResponse rejectRequest(
            Long organizationId,
            UUID requestId,
            AuthorizationContext authorizationContext,
            ProfileChangeDecisionRequest decision
    ) {
        requirePermission(
                authorizationContext,
                PermissionCode.PROFILE_UPDATE_SELF
        );

        ProfileChangeRequest request =
                findRequest(organizationId, requestId);

        OffsetDateTime currentTime = OffsetDateTime.now();

        if (expirationService.expireIfNecessary(
                organizationId,
                requestId,
                currentTime
        )) {
            throw new InvalidProfileChangeStateException(
                    "This profile change request has expired"
            );
        }

        requireTargetUser(request, authorizationContext.getUserId());
        ensurePending(request);

        request.reject(
                authorizationContext.getUserId(),
                requiredDecisionReason(decision),
                currentTime
        );

        ProfileChangeRequest savedRequest =
                requestRepository.save(request);

        auditService.record(
                organizationId,
                authorizationContext.getUserId(),
                SecurityAuditAction.PROFILE_CHANGE_REQUEST_REJECT,
                SecurityAuditOutcome.REJECTED,
                "PROFILE_CHANGE_REQUEST",
                savedRequest.getId(),
                Map.of(
                        "publicRequestId",
                        savedRequest.getPublicId().toString(),
                        "targetUserId",
                        savedRequest.getTargetUserId()
                )
        );

        return toResponse(savedRequest);
    }

    @Override
    public ProfileChangeRequestResponse cancelRequest(
            Long organizationId,
            UUID requestId,
            AuthorizationContext authorizationContext,
            ProfileChangeDecisionRequest decision
    ) {
        requirePermission(
                authorizationContext,
                PermissionCode.USER_PROFILE_UPDATE
        );

        ProfileChangeRequest request =
                findRequest(organizationId, requestId);

        OffsetDateTime currentTime = OffsetDateTime.now();

        if (expirationService.expireIfNecessary(
                organizationId,
                requestId,
                currentTime
        )) {
            throw new InvalidProfileChangeStateException(
                    "This profile change request has expired"
            );
        }

        if (!authorizationContext.getUserId()
                .equals(request.getRequestedByUserId())) {
            throw new ProfileChangeNotAllowedException(
                    "Only the original requester can cancel this request"
            );
        }
        ensurePending(request);

        request.cancel(
                authorizationContext.getUserId(),
                requiredDecisionReason(decision),
                currentTime
        );

        ProfileChangeRequest savedRequest =
                requestRepository.save(request);

        auditService.record(
                organizationId,
                authorizationContext.getUserId(),
                SecurityAuditAction.PROFILE_CHANGE_REQUEST_CANCEL,
                SecurityAuditOutcome.SUCCESS,
                "PROFILE_CHANGE_REQUEST",
                savedRequest.getId(),
                Map.of(
                        "publicRequestId",
                        savedRequest.getPublicId().toString(),
                        "targetUserId",
                        savedRequest.getTargetUserId()
                )
        );

        return toResponse(savedRequest);
    }

    private ProfileChangeRequest findRequest(
            Long organizationId,
            UUID requestId
    ) {
        return requestRepository
                .findByOrganizationIdAndPublicId(
                        organizationId,
                        requestId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profile change request not found"
                ));
    }

    private User findUser(
            Long organizationId,
            Long userId
    ) {
        return userRepository
                .findByOrganizationIdAndId(
                        organizationId,
                        userId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId
                ));
    }

    private void requireTargetUser(
            ProfileChangeRequest request,
            Long currentUserId
    ) {
        if (!currentUserId.equals(request.getTargetUserId())) {
            throw new ProfileChangeNotAllowedException(
                    "Only the affected user can decide this request"
            );
        }
    }

    private void ensurePending(ProfileChangeRequest request) {
        if (!request.isPending()) {
            throw new InvalidProfileChangeStateException(
                    "This profile change request is no longer pending"
            );
        }
    }

    private void ensureOriginalProfileUnchanged(
            ProfileChangeRequest request,
            User user
    ) {
        boolean unchanged =
                Objects.equals(
                        request.getOriginalFirstName(),
                        normalizeRequired(user.getFirstName())
                )
                        && Objects.equals(
                        request.getOriginalMiddleName(),
                        normalizeOptional(user.getMiddleName())
                )
                        && Objects.equals(
                        request.getOriginalLastName(),
                        normalizeOptional(user.getLastName())
                )
                        && Objects.equals(
                        request.getOriginalEmail(),
                        normalizeEmail(user.getEmail())
                )
                        && Objects.equals(
                        request.getOriginalPhone(),
                        normalizeOptional(user.getPhone())
                );

        if (!unchanged) {
            throw new InvalidProfileChangeStateException(
                    "The profile has changed since this request was created"
            );
        }
    }

    private void applyProposedProfile(
            ProfileChangeRequest request,
            User user
    ) {
        user.setFirstName(request.getProposedFirstName());
        user.setMiddleName(request.getProposedMiddleName());
        user.setLastName(request.getProposedLastName());
        user.setEmail(request.getProposedEmail());
        user.setPhone(request.getProposedPhone());
    }

    private void ensureEmailAvailable(
            Long organizationId,
            User targetUser,
            String proposedEmail
    ) {
        if (proposedEmail == null
                || proposedEmail.equals(
                normalizeEmail(targetUser.getEmail())
        )) {
            return;
        }

        if (userRepository.existsByOrganizationIdAndEmailIgnoreCase(
                organizationId,
                proposedEmail
        )) {
            throw new DuplicateResourceException(
                    "Email already exists in this organization"
            );
        }
    }

    private boolean profileMatches(
            User user,
            String firstName,
            String middleName,
            String lastName,
            String email,
            String phone
    ) {
        return Objects.equals(
                normalizeRequired(user.getFirstName()),
                firstName
        )
                && Objects.equals(
                normalizeOptional(user.getMiddleName()),
                middleName
        )
                && Objects.equals(
                normalizeOptional(user.getLastName()),
                lastName
        )
                && Objects.equals(
                normalizeEmail(user.getEmail()),
                email
        )
                && Objects.equals(
                normalizeOptional(user.getPhone()),
                phone
        );
    }

    private ProfileChangeRequestResponse toResponse(
            ProfileChangeRequest request
    ) {
        Map<Long, String> userNames =
                loadDisplayNames(
                        request.getOrganizationId(),
                        List.of(request)
                );

        return toResponse(request, userNames);
    }

    private ProfileChangeRequestResponse toResponse(
            ProfileChangeRequest request,
            Map<Long, String> userNames
    ) {
        String requestedByName =
                userNames.getOrDefault(
                        request.getRequestedByUserId(),
                        "Unknown user"
                );

        String decidedByName =
                request.getDecidedByUserId() == null
                        ? null
                        : userNames.getOrDefault(
                        request.getDecidedByUserId(),
                        "Unknown user"
                );

        return requestMapper.toResponse(
                request,
                requestedByName,
                decidedByName
        );
    }

    private Map<Long, String> loadDisplayNames(
            Long organizationId,
            Collection<ProfileChangeRequest> requests
    ) {
        Set<Long> userIds = new HashSet<>();

        for (ProfileChangeRequest request : requests) {
            userIds.add(request.getRequestedByUserId());

            if (request.getDecidedByUserId() != null) {
                userIds.add(request.getDecidedByUserId());
            }
        }

        if (userIds.isEmpty()) {
            return Map.of();
        }

        return userRepository
                .findAllByOrganizationIdAndIdIn(
                        organizationId,
                        userIds
                )
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        User::getId,
                        this::displayName
                ));
    }

    private String displayName(User user) {
        String name = String.join(
                        " ",
                        normalizeRequired(user.getFirstName()),
                        valueOrEmpty(user.getMiddleName()),
                        valueOrEmpty(user.getLastName())
                )
                .replaceAll("\\s+", " ")
                .trim();

        return name.isBlank() ? user.getUsername() : name;
    }

    private void requirePermission(
            AuthorizationContext authorizationContext,
            PermissionCode permission
    ) {
        if (!authorizationContext.hasPermission(permission)) {
            throw new ProfileChangeNotAllowedException(
                    "You do not have permission to perform this action"
            );
        }
    }

    private String requiredDecisionReason(
            ProfileChangeDecisionRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Decision reason is required"
            );
        }

        return required(request.getReason());
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "A required value is missing"
            );
        }

        return value.trim();
    }

    private String optional(String value) {
        return normalizeOptional(value);
    }

    private String email(String value) {
        return normalizeEmail(value);
    }

    private String normalizeRequired(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String normalizeEmail(String value) {
        String normalized = normalizeOptional(value);

        return normalized == null
                ? null
                : normalized.toLowerCase(Locale.ROOT);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
