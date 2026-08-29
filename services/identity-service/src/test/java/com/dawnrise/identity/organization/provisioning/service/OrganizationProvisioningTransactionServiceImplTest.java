package com.dawnrise.identity.organization.provisioning.service;

import com.dawnrise.identity.auth.activation.event.UserActivationRequestedEvent;
import com.dawnrise.identity.common.phone.PhoneNumberNormalizer;
import com.dawnrise.identity.organization.entity.Organization;
import com.dawnrise.identity.organization.enums.OrganizationStatus;
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
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationProvisioningTransactionServiceImplTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationProvisioningRequestRepository
            provisioningRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private PhoneNumberNormalizer phoneNumberNormalizer;

    @Test
    void provisionAtomically_whenRequestIsValid_createsOrganizationAndAuthority()
            throws Exception {
        OrganizationProvisioningTransactionServiceImpl service =
                service();
        ProvisionOrganizationRequest request = request();
        OrganizationProvisioningRequest provisioningRequest =
                provisioningRequest();

        when(provisioningRequestRepository.findById(10L))
                .thenReturn(Optional.of(provisioningRequest));
        when(organizationRepository.existsById(1L))
                .thenReturn(false);
        when(organizationRepository.findBySchoolCode("SCH001"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByOrganizationIdAndUsername(
                1L,
                "authority.one"
        )).thenReturn(false);
        when(userRepository.existsByOrganizationIdAndEmail(
                1L,
                "authority@dawnrise.com"
        )).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    ReflectionTestUtils.setField(user, "id", 20L);
                    return user;
                });
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"organizationId\":1}");
        when(phoneNumberNormalizer.normalizeOptional("9876543211"))
                .thenReturn("+919876543211");

        ProvisionOrganizationResponse response =
                service.provisionAtomically(10L, request);

        assertEquals(1L, response.getOrganizationId());
        assertEquals(
                OrganizationStatus.ACTIVE,
                response.getOrganizationStatus()
        );
        assertEquals(20L, response.getAuthorityUserId());
        assertEquals(
                UserStatus.PENDING_ACTIVATION,
                response.getAuthorityStatus()
        );
        assertEquals(
                ProvisioningRequestStatus.SUCCEEDED,
                provisioningRequest.getStatus()
        );

        ArgumentCaptor<Organization> organizationCaptor =
                ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(
                organizationCaptor.capture()
        );
        assertEquals(
                OrganizationStatus.ACTIVE,
                organizationCaptor.getValue().getStatus()
        );

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(
                userCaptor.capture()
        );
        assertEquals(1L, userCaptor.getValue().getOrganizationId());
        assertEquals(
                "authority.one",
                userCaptor.getValue().getUsername()
        );
        assertEquals("+919876543211", userCaptor.getValue().getPhone());
        assertTrue(userCaptor.getValue()
                .getRoles()
                .contains(UserRole.GOVERNING_AUTHORITY));

        ArgumentCaptor<UserActivationRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        UserActivationRequestedEvent.class
                );
        verify(eventPublisher).publishEvent(
                eventCaptor.capture()
        );
        assertEquals(20L, eventCaptor.getValue().userId());
    }

    @Test
    void provisionAtomically_whenOrganizationAlreadyExists_rejectsConflict() {
        OrganizationProvisioningTransactionServiceImpl service =
                service();
        ProvisionOrganizationRequest request = request();

        when(provisioningRequestRepository.findById(10L))
                .thenReturn(Optional.of(provisioningRequest()));
        when(organizationRepository.existsById(1L))
                .thenReturn(true);

        assertThrows(
                ProvisioningConflictException.class,
                () -> service.provisionAtomically(10L, request)
        );

        verify(userRepository, never())
                .saveAndFlush(any(User.class));
    }

    @Test
    void provisionAtomically_whenUsernameExists_rejectsWithSafeFieldConflict() {
        OrganizationProvisioningTransactionServiceImpl service =
                service();
        ProvisionOrganizationRequest request = request();

        when(provisioningRequestRepository.findById(10L))
                .thenReturn(Optional.of(provisioningRequest()));
        when(organizationRepository.existsById(1L))
                .thenReturn(false);
        when(organizationRepository.findBySchoolCode("SCH001"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByOrganizationIdAndUsername(
                1L,
                "authority.one"
        )).thenReturn(true);

        ProvisioningConflictException exception = assertThrows(
                ProvisioningConflictException.class,
                () -> service.provisionAtomically(10L, request)
        );

        assertEquals("authority.username", exception.getField());
        assertTrue(exception.hasField());
        assertEquals(
                "This username is already registered.",
                exception.getMessage()
        );
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void provisionAtomically_whenEmailExists_rejectsWithSafeFieldConflict() {
        OrganizationProvisioningTransactionServiceImpl service =
                service();
        ProvisionOrganizationRequest request = request();

        when(provisioningRequestRepository.findById(10L))
                .thenReturn(Optional.of(provisioningRequest()));
        when(organizationRepository.existsById(1L))
                .thenReturn(false);
        when(organizationRepository.findBySchoolCode("SCH001"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByOrganizationIdAndUsername(
                1L,
                "authority.one"
        )).thenReturn(false);
        when(userRepository.existsByOrganizationIdAndEmail(
                1L,
                "authority@dawnrise.com"
        )).thenReturn(true);

        ProvisioningConflictException exception = assertThrows(
                ProvisioningConflictException.class,
                () -> service.provisionAtomically(10L, request)
        );

        assertEquals("authority.email", exception.getField());
        assertTrue(exception.hasField());
        assertEquals(
                "This email address is already registered.",
                exception.getMessage()
        );
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    private OrganizationProvisioningTransactionServiceImpl service() {
        return new OrganizationProvisioningTransactionServiceImpl(
                organizationRepository,
                provisioningRequestRepository,
                userRepository,
                eventPublisher,
                objectMapper,
                phoneNumberNormalizer
        );
    }

    private OrganizationProvisioningRequest provisioningRequest() {
        OrganizationProvisioningRequest request =
                new OrganizationProvisioningRequest(
                        "school-provisioning:1:10",
                        1L,
                        "hash"
                );
        ReflectionTestUtils.setField(request, "id", 10L);
        return request;
    }

    private ProvisionOrganizationRequest request() {
        InitialAuthorityRequest authority =
                new InitialAuthorityRequest();
        authority.setUsername("authority.one");
        authority.setFirstName("Authority");
        authority.setMiddleName("Middle");
        authority.setLastName("One");
        authority.setEmail("authority@dawnrise.com");
        authority.setPhone("9876543211");

        ProvisionOrganizationRequest request =
                new ProvisionOrganizationRequest();
        request.setOrganizationId(1L);
        request.setSchoolCode("SCH001");
        request.setSchoolName("Dawnrise Public School");
        request.setSchoolEmail("school@dawnrise.com");
        request.setAuthority(authority);
        return request;
    }
}
