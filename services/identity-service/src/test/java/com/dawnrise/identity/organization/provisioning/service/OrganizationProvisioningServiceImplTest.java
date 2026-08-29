package com.dawnrise.identity.organization.provisioning.service;

import com.dawnrise.identity.organization.enums.OrganizationStatus;
import com.dawnrise.identity.organization.provisioning.dto.InitialAuthorityRequest;
import com.dawnrise.identity.organization.provisioning.dto.ProvisionOrganizationRequest;
import com.dawnrise.identity.organization.provisioning.dto.ProvisionOrganizationResponse;
import com.dawnrise.identity.organization.provisioning.entity.OrganizationProvisioningRequest;
import com.dawnrise.identity.organization.provisioning.enums.ProvisioningRequestStatus;
import com.dawnrise.identity.organization.provisioning.security.ProvisioningRequestHasher;
import com.dawnrise.identity.user.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationProvisioningServiceImplTest {

    @Mock
    private ProvisioningRequestHasher requestHasher;

    @Mock
    private ProvisioningRequestLifecycleService lifecycleService;

    @Mock
    private OrganizationProvisioningTransactionService transactionService;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void provision_whenIdempotencyKeyIsBlank_rejectsRequest() {
        OrganizationProvisioningServiceImpl service = service();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.provision(" ", request())
        );

        verifyNoInteractions(
                requestHasher,
                lifecycleService,
                transactionService
        );
    }

    @Test
    void provision_whenPreviousRequestSucceeded_returnsStoredResponse()
            throws Exception {
        OrganizationProvisioningServiceImpl service = service();
        ProvisionOrganizationRequest request = request();
        OrganizationProvisioningRequest existing =
                new OrganizationProvisioningRequest(
                        "school-provisioning:1:10",
                        1L,
                        "hash"
                );
        existing.markSucceeded("{\"organizationId\":1}");

        ProvisionOrganizationResponse expected =
                new ProvisionOrganizationResponse(
                        1L,
                        OrganizationStatus.ACTIVE,
                        2L,
                        UserStatus.PENDING_ACTIVATION,
                        ProvisioningRequestStatus.SUCCEEDED
                );

        when(requestHasher.hash(request)).thenReturn("hash");
        when(lifecycleService.prepareRequest(
                "school-provisioning:1:10",
                1L,
                "hash"
        )).thenReturn(existing);
        when(objectMapper.readValue(
                "{\"organizationId\":1}",
                ProvisionOrganizationResponse.class
        )).thenReturn(expected);

        ProvisionOrganizationResponse actual =
                service.provision(
                        " school-provisioning:1:10 ",
                        request
                );

        assertSame(expected, actual);
        verifyNoInteractions(transactionService);
    }

    @Test
    void provision_whenTransactionFails_recordsFailureAndRethrows() {
        OrganizationProvisioningServiceImpl service = service();
        ProvisionOrganizationRequest request = request();
        OrganizationProvisioningRequest processing =
                new OrganizationProvisioningRequest(
                        "school-provisioning:1:10",
                        1L,
                        "hash"
                );
        ReflectionTestUtils.setField(processing, "id", 10L);

        RuntimeException failure =
                new RuntimeException("database timeout");

        when(requestHasher.hash(request)).thenReturn("hash");
        when(lifecycleService.prepareRequest(
                "school-provisioning:1:10",
                1L,
                "hash"
        )).thenReturn(processing);
        when(transactionService.provisionAtomically(10L, request))
                .thenThrow(failure);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> service.provision(
                        "school-provisioning:1:10",
                        request
                )
        );

        assertSame(failure, actual);
        verify(lifecycleService).markFailed(
                10L,
                "database timeout"
        );
    }

    private OrganizationProvisioningServiceImpl service() {
        return new OrganizationProvisioningServiceImpl(
                requestHasher,
                lifecycleService,
                transactionService,
                objectMapper
        );
    }

    private ProvisionOrganizationRequest request() {
        InitialAuthorityRequest authority =
                new InitialAuthorityRequest();
        authority.setUsername("authority.one");
        authority.setFirstName("Authority");
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
