package com.dawnrise.identity.organization.provisioning.service;

import com.dawnrise.identity.organization.provisioning.entity.OrganizationProvisioningRequest;
import com.dawnrise.identity.organization.provisioning.enums.ProvisioningRequestStatus;
import com.dawnrise.identity.organization.provisioning.exception.ProvisioningConflictException;
import com.dawnrise.identity.organization.provisioning.exception.ProvisioningInProgressException;
import com.dawnrise.identity.organization.provisioning.repository.OrganizationProvisioningRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProvisioningRequestLifecycleServiceImplTest {

    @Mock
    private OrganizationProvisioningRequestRepository repository;

    @Test
    void prepareRequest_whenKeyIsNew_createsProcessingRequest() {
        ProvisioningRequestLifecycleServiceImpl service =
                new ProvisioningRequestLifecycleServiceImpl(repository);

        OrganizationProvisioningRequest saved =
                new OrganizationProvisioningRequest(
                        "school-provisioning:1:10",
                        1L,
                        "hash"
                );

        when(repository.findByIdempotencyKey(
                "school-provisioning:1:10"
        )).thenReturn(Optional.empty());
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .thenReturn(saved);

        OrganizationProvisioningRequest actual =
                service.prepareRequest(
                        "school-provisioning:1:10",
                        1L,
                        "hash"
                );

        assertSame(saved, actual);
        assertEquals(
                ProvisioningRequestStatus.PROCESSING,
                actual.getStatus()
        );
    }

    @Test
    void prepareRequest_whenSameKeyHasDifferentPayload_rejectsConflict() {
        ProvisioningRequestLifecycleServiceImpl service =
                new ProvisioningRequestLifecycleServiceImpl(repository);

        OrganizationProvisioningRequest existing =
                new OrganizationProvisioningRequest(
                        "school-provisioning:1:10",
                        1L,
                        "hash-one"
                );

        when(repository.findByIdempotencyKey(
                "school-provisioning:1:10"
        )).thenReturn(Optional.of(existing));

        assertThrows(
                ProvisioningConflictException.class,
                () -> service.prepareRequest(
                        "school-provisioning:1:10",
                        1L,
                        "hash-two"
                )
        );
    }

    @Test
    void prepareRequest_whenSameRequestIsProcessing_rejectsConcurrentRetry() {
        ProvisioningRequestLifecycleServiceImpl service =
                new ProvisioningRequestLifecycleServiceImpl(repository);

        OrganizationProvisioningRequest existing =
                new OrganizationProvisioningRequest(
                        "school-provisioning:1:10",
                        1L,
                        "hash"
                );

        when(repository.findByIdempotencyKey(
                "school-provisioning:1:10"
        )).thenReturn(Optional.of(existing));

        assertThrows(
                ProvisioningInProgressException.class,
                () -> service.prepareRequest(
                        "school-provisioning:1:10",
                        1L,
                        "hash"
                )
        );
    }

    @Test
    void markFailed_whenProcessing_sanitizesAndTruncatesError() {
        ProvisioningRequestLifecycleServiceImpl service =
                new ProvisioningRequestLifecycleServiceImpl(repository);

        OrganizationProvisioningRequest existing =
                new OrganizationProvisioningRequest(
                        "school-provisioning:1:10",
                        1L,
                        "hash"
                );

        when(repository.findById(10L))
                .thenReturn(Optional.of(existing));

        service.markFailed(10L, "x".repeat(600));

        assertEquals(
                ProvisioningRequestStatus.FAILED,
                existing.getStatus()
        );
        assertEquals(500, existing.getErrorSummary().length());
        verify(repository).findById(10L);
    }
}
