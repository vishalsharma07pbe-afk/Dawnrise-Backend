package com.dawnrise.identity.organization.provisioning.service;

import com.dawnrise.identity.auth.activation.event.UserActivationRequestedEvent;
import com.dawnrise.identity.common.phone.PhoneNumberNormalizer;
import com.dawnrise.identity.organization.entity.Organization;
import com.dawnrise.identity.organization.provisioning.dto.InitialAuthorityRequest;
import com.dawnrise.identity.organization.provisioning.dto.ProvisionOrganizationRequest;
import com.dawnrise.identity.organization.provisioning.dto.ProvisionOrganizationResponse;
import com.dawnrise.identity.organization.provisioning.entity.OrganizationProvisioningRequest;
import com.dawnrise.identity.organization.provisioning.enums.ProvisioningRequestStatus;
import com.dawnrise.identity.organization.provisioning.exception.ProvisioningConflictException;
import com.dawnrise.identity.organization.provisioning.repository.OrganizationProvisioningRequestRepository;
import com.dawnrise.identity.organization.repository.OrganizationRepository;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.repository.UserRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class OrganizationProvisioningTransactionServiceImpl
        implements OrganizationProvisioningTransactionService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationProvisioningRequestRepository
            provisioningRequestRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final PhoneNumberNormalizer phoneNumberNormalizer;

    public OrganizationProvisioningTransactionServiceImpl(
            OrganizationRepository organizationRepository,
            OrganizationProvisioningRequestRepository
                    provisioningRequestRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            PhoneNumberNormalizer phoneNumberNormalizer
    ) {
        this.organizationRepository = organizationRepository;
        this.provisioningRequestRepository =
                provisioningRequestRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
    }

    @Override
    @Transactional
    public ProvisionOrganizationResponse provisionAtomically(
            Long provisioningRequestId,
            ProvisionOrganizationRequest request
    ) {
        OrganizationProvisioningRequest provisioningRequest =
                provisioningRequestRepository
                        .findById(provisioningRequestId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Provisioning request not found: "
                                        + provisioningRequestId
                        ));

        if (provisioningRequest.getStatus()
                != ProvisioningRequestStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Provisioning request must be in PROCESSING state"
            );
        }

        validateOrganizationDoesNotExist(request);
        validateAuthorityDoesNotExist(request);

        Organization organization = new Organization(
                request.getOrganizationId(),
                request.getSchoolCode(),
                request.getSchoolName(),
                request.getSchoolEmail()
        );

        organization.completeProvisioning();
        organizationRepository.save(organization);

        InitialAuthorityRequest authorityRequest =
                request.getAuthority();

        User authorityUser = new User(
                request.getOrganizationId(),
                authorityRequest.getUsername(),
                authorityRequest.getFirstName(),
                Set.of(UserRole.GOVERNING_AUTHORITY)
        );

        authorityUser.setMiddleName(
                authorityRequest.getMiddleName()
        );
        authorityUser.setLastName(
                authorityRequest.getLastName()
        );
        authorityUser.setEmail(
                authorityRequest.getEmail()
        );
        authorityUser.setPhone(
                phoneNumberNormalizer.normalizeOptional(
                        authorityRequest.getPhone()
                )
        );

        User savedAuthority =
                userRepository.saveAndFlush(authorityUser);

        ProvisionOrganizationResponse response =
                new ProvisionOrganizationResponse(
                        organization.getId(),
                        organization.getStatus(),
                        savedAuthority.getId(),
                        savedAuthority.getStatus(),
                        ProvisioningRequestStatus.SUCCEEDED
                );

        provisioningRequest.markSucceeded(
                serializeResponse(response)
        );

        eventPublisher.publishEvent(
                new UserActivationRequestedEvent(
                        savedAuthority.getId()
                )
        );

        return response;
    }

    private void validateOrganizationDoesNotExist(
            ProvisionOrganizationRequest request
    ) {
        if (organizationRepository.existsById(
                request.getOrganizationId()
        )) {
            throw new ProvisioningConflictException(
                    "organizationId",
                    "An organization already exists for this school."
            );
        }

        organizationRepository
                .findBySchoolCode(request.getSchoolCode())
                .ifPresent(existing -> {
                    throw new ProvisioningConflictException(
                            "schoolCode",
                            "This school code is already assigned."
                    );
                });
    }

    private void validateAuthorityDoesNotExist(
            ProvisionOrganizationRequest request
    ) {
        InitialAuthorityRequest authority =
                request.getAuthority();

        if (userRepository.existsByOrganizationIdAndUsername(
                request.getOrganizationId(),
                authority.getUsername()
        )) {
            throw new ProvisioningConflictException(
                    "authority.username",
                    "This username is already registered."
            );
        }

        if (userRepository.existsByOrganizationIdAndEmail(
                request.getOrganizationId(),
                authority.getEmail()
        )) {
            throw new ProvisioningConflictException(
                    "authority.email",
                    "This email address is already registered."
            );
        }
    }

    private String serializeResponse(
            ProvisionOrganizationResponse response
    ) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Could not serialize provisioning response",
                    exception
            );
        }
    }
}
