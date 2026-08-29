package com.dawnrise.school.school.service;

import com.dawnrise.school.common.dto.PageResponse;
import com.dawnrise.school.school.DTO.AuthorityCorrectionRequest;
import com.dawnrise.school.school.DTO.InitialAuthorityRequest;
import com.dawnrise.school.school.DTO.SchoolOnboardingRequest;
import com.dawnrise.school.school.DTO.SchoolProvisioningResponse;
import com.dawnrise.school.school.DTO.SchoolResponse;
import com.dawnrise.school.school.DTO.UpdateSchoolRequest;
import com.dawnrise.school.school.entity.School;
import com.dawnrise.school.school.entity.SchoolProvisioning;
import com.dawnrise.school.school.enums.ProvisioningStatus;
import com.dawnrise.school.school.exception.DuplicateResourceException;
import com.dawnrise.school.school.exception.ResourceNotFoundException;
import com.dawnrise.school.school.exception.InvalidRequestException;
import com.dawnrise.school.school.mapper.SchoolMapper;
import com.dawnrise.school.school.phone.PhoneNumberNormalizer;
import com.dawnrise.school.school.provisioning.IdentityProvisioningClient;
import com.dawnrise.school.school.provisioning.IdentityProvisioningRequest;
import com.dawnrise.school.school.provisioning.IdentityProvisioningResponse;
import com.dawnrise.school.school.repository.SchoolProvisioningRepository;
import com.dawnrise.school.school.repository.schoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.dawnrise.school.school.enums.SchoolStatus;
import com.dawnrise.school.school.provisioning.IdentityProvisioningException;

import static com.dawnrise.school.school.enums.SchoolStatus.ACTIVE;
import static com.dawnrise.school.school.enums.SchoolStatus.INACTIVE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchoolServiceImplTest {

    @Mock
    private schoolRepository schoolRepository;

    @Mock
    private SchoolProvisioningRepository provisioningRepository;

    @Mock
    private SchoolMapper schoolMapper;

    @Mock
    private IdentityProvisioningClient identityProvisioningClient;

    @Mock
    private PhoneNumberNormalizer phoneNumberNormalizer;

    private SchoolServiceImpl schoolService;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        doAnswer(invocation -> {
            Consumer<?> callback = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            Consumer<Object> transactionCallback = (Consumer<Object>) callback;
            transactionCallback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        schoolService = new SchoolServiceImpl(
                schoolRepository,
                provisioningRepository,
                schoolMapper,
                identityProvisioningClient,
                transactionTemplate,
                phoneNumberNormalizer
        );

        when(phoneNumberNormalizer.normalizeRequired(
                eq("phone"),
                anyString()
        )).thenAnswer(invocation -> invocation.getArgument(1));
        when(phoneNumberNormalizer.normalizeRequired(
                eq("initialAuthority.phone"),
                anyString()
        )).thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void getSchoolById_whenSchoolExists_returnsSchoolResponse() {
        // Arrange
        long schoolId = 1L;

        School school = mock(School.class);
        SchoolResponse expectedResponse = mock(SchoolResponse.class);

        when(schoolRepository.findById(schoolId))
                .thenReturn(Optional.of(school));

        when(schoolMapper.toResponse(school))
                .thenReturn(expectedResponse);

        // Act
        SchoolResponse actualResponse =
                schoolService.getSchoolById(schoolId);

        // Assert
        assertSame(expectedResponse, actualResponse);

        verify(schoolRepository).findById(schoolId);
        verify(schoolMapper).toResponse(school);
    }

    @Test
    void getSchoolById_whenSchoolDoesNotExist_throwsResourceNotFoundException() {
        long schoolId = 1L;
        when(schoolRepository.findById(schoolId))
                .thenReturn(Optional.empty());

        //Act and Assert
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> schoolService.getSchoolById(schoolId)
        );

        assertSame("School not found", exception.getMessage());

        verify(schoolRepository).findById(schoolId);
        verifyNoInteractions(schoolMapper);
    }

    @Test
    void onboardSchool_whenIdentityProvisioningSucceeds_activatesSchool() {
        SchoolOnboardingRequest request = onboardingRequest();
        School school = mock(School.class);
        School savedSchool = mock(School.class);
        SchoolProvisioning provisioning = provisioning();

        when(request.getSchoolCode()).thenReturn("schC001");
        when(schoolRepository.existsBySchoolCode("schC001")).thenReturn(false);
        when(schoolMapper.toEntity(request)).thenReturn(school);
        when(schoolRepository.save(school)).thenReturn(savedSchool);
        when(savedSchool.getId()).thenReturn(1L);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(savedSchool));
        when(provisioningRepository.save(any(SchoolProvisioning.class))).thenReturn(provisioning);
        when(provisioningRepository.findWithLockBySchoolId(1L)).thenReturn(Optional.of(provisioning));
        when(provisioningRepository.findBySchoolId(1L)).thenReturn(Optional.of(provisioning));
        when(savedSchool.getStatus()).thenReturn(ACTIVE);
        when(identityProvisioningClient.provisionInitialAuthority(
                any(IdentityProvisioningRequest.class),
                anyString()
        )).thenReturn(identitySuccessResponse());

        SchoolProvisioningResponse actualResponse =
                schoolService.onboardSchool(request);

        assertEquals(1L, actualResponse.getSchoolId());

        verify(schoolRepository).existsBySchoolCode("schC001");
        verify(schoolMapper).toEntity(request);
        verify(schoolRepository).save(school);
        verify(identityProvisioningClient).provisionInitialAuthority(
                any(IdentityProvisioningRequest.class),
                anyString()
        );
        verify(savedSchool).activate();
    }

    @Test
    void onboardSchool_whenCodeIsNotUnique_throwsDuplicateCodeException() {
        SchoolOnboardingRequest request = mock(SchoolOnboardingRequest.class);

        when(request.getSchoolCode()).thenReturn("schC001");
        when(schoolRepository.existsBySchoolCode("schC001")).thenReturn(true);
        //Act and Assert
        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> schoolService.onboardSchool(request)
        );
        assertEquals("School code already exists", exception.getMessage());

        verify(schoolRepository).existsBySchoolCode("schC001");
        verifyNoInteractions(schoolMapper);
        verify(schoolRepository,never()).save(any(School.class));
    }

    @Test
    void onboardSchool_whenIdentityFails_marksProvisioningFailed() {
        SchoolOnboardingRequest request = onboardingRequest();
        School school = mock(School.class);
        School savedSchool = mock(School.class);
        SchoolProvisioning provisioning = provisioning();

        when(request.getSchoolCode()).thenReturn("schC001");
        when(schoolRepository.existsBySchoolCode("schC001")).thenReturn(false);
        when(schoolMapper.toEntity(request)).thenReturn(school);
        when(schoolRepository.save(school)).thenReturn(savedSchool);
        when(savedSchool.getId()).thenReturn(1L);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(savedSchool));
        when(provisioningRepository.findWithLockBySchoolId(1L)).thenReturn(Optional.of(provisioning));
        when(provisioningRepository.findBySchoolId(1L)).thenReturn(Optional.of(provisioning));
        doThrow(new RuntimeException("connection refused with token abc"))
                .when(identityProvisioningClient)
                .provisionInitialAuthority(any(), anyString());

        schoolService.onboardSchool(request);

        verify(provisioning).fail(
                "IDENTITY_SERVICE_ERROR",
                null,
                "Identity provisioning could not be completed."
        );
        verify(savedSchool).markProvisioningFailed();
    }

    @Test
    void onboardSchool_whenIdentityValidationFails_marksProvisioningWithFieldError() {
        SchoolOnboardingRequest request = onboardingRequest();
        School school = mock(School.class);
        School savedSchool = mock(School.class);
        SchoolProvisioning provisioning = provisioning();

        when(request.getSchoolCode()).thenReturn("schC001");
        when(schoolRepository.existsBySchoolCode("schC001")).thenReturn(false);
        when(schoolMapper.toEntity(request)).thenReturn(school);
        when(schoolRepository.save(school)).thenReturn(savedSchool);
        when(savedSchool.getId()).thenReturn(1L);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(savedSchool));
        when(provisioningRepository.findWithLockBySchoolId(1L)).thenReturn(Optional.of(provisioning));
        when(provisioningRepository.findBySchoolId(1L)).thenReturn(Optional.of(provisioning));
        doThrow(new IdentityProvisioningException(
                400,
                "Validation failed",
                Map.of("authority.email", "Email is already in use")
        ))
                .when(identityProvisioningClient)
                .provisionInitialAuthority(any(), anyString());

        schoolService.onboardSchool(request);

        verify(provisioning).fail(
                "IDENTITY_VALIDATION_FAILED",
                "initialAuthority.email",
                "Email is already in use"
        );
        verify(savedSchool).markProvisioningFailed();
    }

    @Test
    void updateSchool_whenSchoolExists_returnsSchoolResponse() {
        Long id = 1L;
        UpdateSchoolRequest request = mock(UpdateSchoolRequest.class);
        School existingschool = mock(School.class);
        School updatedSchool = mock(School.class);
        SchoolResponse expectedResponse = mock(SchoolResponse.class);

        when(schoolRepository.findById(id)).thenReturn(Optional.of(existingschool));
        when(schoolRepository.save(existingschool)).thenReturn(updatedSchool);
        when(schoolMapper.toResponse(updatedSchool)).thenReturn(expectedResponse);

        SchoolResponse actualResponse = schoolService.updateSchool(id, request);
        assertSame(expectedResponse, actualResponse);

        verify(schoolRepository).findById(id);
        verify(schoolMapper).updateEntity(request, existingschool);
        verify(schoolRepository).save(existingschool);
        verify(schoolMapper).toResponse(updatedSchool);
    }

    @Test
    void updateSchool_whenSchoolDoesNotExist_throwsResourceNotFoundException() {
        long id = 99L;
        UpdateSchoolRequest request = mock(UpdateSchoolRequest.class);

        when(schoolRepository.findById(id)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                ()->schoolService.updateSchool(id, request));

        assertEquals("School not found", exception.getMessage());

        verify(schoolRepository).findById(id);
        verifyNoInteractions(schoolMapper);
        verify(schoolRepository,never()).save(any(School.class));
    }

    @Test
    void deleteSchool_whenSchoolExists_returnsSchoolResponse() {
        Long id = 1L;
        School school = mock(School.class);

        when(schoolRepository.findById(id)).thenReturn(Optional.of(school));
        when(schoolRepository.save(school)).thenReturn(school);

        schoolService.deleteSchool(id);

        verify(schoolRepository).findById(id);
        verify(school).deactivate();
        verify(schoolRepository).save(school);
    }

    @Test
    void deleteSchool_whenSchoolDoesNotExist_throwsResourceNotFoundException() {
        Long id = 99L;
        when(schoolRepository.findById(id)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                ()->schoolService.deleteSchool(id));

        assertEquals("School not found", exception.getMessage());

        verify(schoolRepository).findById(id);
        verify(schoolRepository,never()).save(any(School.class));
    }

    @Test
    void restoreSchool_whenSchoolExists_returnsSchoolResponse() {
        Long id = 1L;
        School school = mock(School.class);
        SchoolResponse expectedResponse = mock(SchoolResponse.class);
        SchoolProvisioning provisioning = provisioningSucceeded();

        when(schoolRepository.findById(id)).thenReturn(Optional.of(school));
        when(provisioningRepository.findBySchoolId(id))
                .thenReturn(Optional.of(provisioning));
        when(schoolRepository.save(school)).thenReturn(school);
        when(schoolMapper.toResponse(school)).thenReturn(expectedResponse);

        SchoolResponse actualResponse = schoolService.restoreSchool(id);

        assertSame(expectedResponse, actualResponse);
        verify(schoolRepository).findById(id);
        verify(school).activate();
        verify(schoolRepository).save(school);
        verify(schoolMapper).toResponse(school);
    }

    @Test
    void restoreSchool_whenProvisioningNeverSucceeded_doesNotActivate() {
        Long id = 1L;
        School school = mock(School.class);
        SchoolProvisioning provisioning = mock(SchoolProvisioning.class);

        when(schoolRepository.findById(id)).thenReturn(Optional.of(school));
        when(provisioningRepository.findBySchoolId(id)).thenReturn(Optional.of(provisioning));
        when(provisioning.getStatus()).thenReturn(ProvisioningStatus.FAILED);

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> schoolService.restoreSchool(id)
        );

        assertEquals("School identity provisioning has not succeeded", exception.getMessage());
        verify(school, never()).activate();
        verify(schoolRepository, never()).save(any(School.class));
    }

    @Test
    void retryProvisioning_whenFailed_usesSameIdempotencyKeyAndSucceeds() {
        Long id = 1L;
        School school = mock(School.class);
        SchoolProvisioning provisioning = provisioning();

        when(provisioning.getStatus()).thenReturn(ProvisioningStatus.FAILED, ProvisioningStatus.PENDING);
        when(schoolRepository.findById(id)).thenReturn(Optional.of(school));
        when(provisioningRepository.findBySchoolId(id)).thenReturn(Optional.of(provisioning));
        when(provisioningRepository.findWithLockBySchoolId(id)).thenReturn(Optional.of(provisioning));
        when(identityProvisioningClient.provisionInitialAuthority(
                any(IdentityProvisioningRequest.class),
                anyString()
        )).thenReturn(identitySuccessResponse());

        schoolService.retryProvisioning(id);

        verify(identityProvisioningClient).provisionInitialAuthority(
                any(IdentityProvisioningRequest.class),
                eq("school-provisioning:1:10")
        );
        verify(provisioning).startAttempt();
        verify(provisioning).succeed();
        verify(school).activate();
    }

    @Test
    void retryProvisioning_whenAttemptAlreadyPending_rejectsConcurrentRetry() {
        Long id = 1L;
        SchoolProvisioning provisioning = provisioning();
        when(provisioning.getStatus()).thenReturn(ProvisioningStatus.FAILED, ProvisioningStatus.PENDING);
        when(provisioning.getAttemptCount()).thenReturn(1);
        when(provisioningRepository.findBySchoolId(id)).thenReturn(Optional.of(provisioning));
        when(schoolRepository.findById(id)).thenReturn(Optional.of(mock(School.class)));
        when(provisioningRepository.findWithLockBySchoolId(id)).thenReturn(Optional.of(provisioning));

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> schoolService.retryProvisioning(id)
        );

        assertEquals("School provisioning is already in progress", exception.getMessage());
        verifyNoInteractions(identityProvisioningClient);
    }

    @Test
    void correctProvisioningAuthority_whenFailed_updatesSnapshotAndClearsFailure() {
        Long id = 1L;
        School school = mock(School.class);
        SchoolProvisioning provisioning = provisioning();
        AuthorityCorrectionRequest request = authorityCorrectionRequest();

        when(schoolRepository.findById(id)).thenReturn(Optional.of(school));
        when(provisioningRepository.findWithLockBySchoolId(id))
                .thenReturn(Optional.of(provisioning));
        when(provisioningRepository.findBySchoolId(id))
                .thenReturn(Optional.of(provisioning));
        when(provisioning.getStatus()).thenReturn(ProvisioningStatus.FAILED);

        schoolService.correctProvisioningAuthority(id, request);

        verify(provisioning).correctAuthority(
                "Corrected",
                "Middle",
                "Authority",
                "corrected.authority",
                "corrected@dawnrise.com",
                "9876543212"
        );
        verify(school).markProvisioningFailed();
        verifyNoInteractions(identityProvisioningClient);
    }

    @Test
    void correctProvisioningAuthority_whenPending_rejectsCorrection() {
        Long id = 1L;
        School school = mock(School.class);
        SchoolProvisioning provisioning = provisioning();

        when(schoolRepository.findById(id)).thenReturn(Optional.of(school));
        when(provisioningRepository.findWithLockBySchoolId(id))
                .thenReturn(Optional.of(provisioning));
        when(provisioning.getStatus()).thenReturn(ProvisioningStatus.PENDING);

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> schoolService.correctProvisioningAuthority(
                        id,
                        authorityCorrectionRequest()
                )
        );

        assertEquals(
                "School provisioning is already in progress",
                exception.getMessage()
        );
        verify(provisioning, never()).correctAuthority(
                anyString(),
                any(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
    }

    @Test
    void correctProvisioningAuthority_whenSucceeded_rejectsCorrection() {
        Long id = 1L;
        School school = mock(School.class);
        SchoolProvisioning provisioning = provisioning();

        when(schoolRepository.findById(id)).thenReturn(Optional.of(school));
        when(provisioningRepository.findWithLockBySchoolId(id))
                .thenReturn(Optional.of(provisioning));
        when(provisioning.getStatus()).thenReturn(ProvisioningStatus.SUCCEEDED);

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> schoolService.correctProvisioningAuthority(
                        id,
                        authorityCorrectionRequest()
                )
        );

        assertEquals(
                "School provisioning already succeeded",
                exception.getMessage()
        );
        verify(provisioning, never()).correctAuthority(
                anyString(),
                any(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
    }

    @Test
    void retryProvisioning_withoutCorrection_reusesLegacyIdempotencyKey() {
        Long id = 1L;
        School school = mock(School.class);
        SchoolProvisioning provisioning = provisioning();

        when(provisioning.getStatus()).thenReturn(ProvisioningStatus.FAILED);
        when(provisioning.getRequestRevision()).thenReturn(0);
        when(schoolRepository.findById(id)).thenReturn(Optional.of(school));
        when(provisioningRepository.findBySchoolId(id)).thenReturn(Optional.of(provisioning));
        when(provisioningRepository.findWithLockBySchoolId(id)).thenReturn(Optional.of(provisioning));
        when(identityProvisioningClient.provisionInitialAuthority(
                any(IdentityProvisioningRequest.class),
                anyString()
        )).thenReturn(identitySuccessResponse());

        schoolService.retryProvisioning(id);

        verify(identityProvisioningClient).provisionInitialAuthority(
                any(IdentityProvisioningRequest.class),
                eq("school-provisioning:1:10")
        );
    }

    @Test
    void retryProvisioning_afterCorrection_usesRevisionIdempotencyKey() {
        Long id = 1L;
        School school = mock(School.class);
        SchoolProvisioning provisioning = provisioning();

        when(provisioning.getStatus()).thenReturn(ProvisioningStatus.FAILED);
        when(provisioning.getRequestRevision()).thenReturn(1);
        when(schoolRepository.findById(id)).thenReturn(Optional.of(school));
        when(provisioningRepository.findBySchoolId(id)).thenReturn(Optional.of(provisioning));
        when(provisioningRepository.findWithLockBySchoolId(id)).thenReturn(Optional.of(provisioning));
        when(identityProvisioningClient.provisionInitialAuthority(
                any(IdentityProvisioningRequest.class),
                anyString()
        )).thenReturn(identitySuccessResponse());

        schoolService.retryProvisioning(id);

        verify(identityProvisioningClient).provisionInitialAuthority(
                any(IdentityProvisioningRequest.class),
                eq("school-provisioning:1:10:revision:1")
        );
    }

    @Test
    void schoolProvisioningLifecycle_clearsAndStoresStructuredFailures() {
        SchoolProvisioning provisioning = new SchoolProvisioning(
                1L,
                "Authority",
                null,
                "One",
                "authority.one",
                "authority@dawnrise.com",
                "9876543211"
        );

        provisioning.fail(
                "IDENTITY_CONFLICT",
                "initialAuthority.email",
                "This email address is already registered."
        );

        assertEquals("IDENTITY_CONFLICT", provisioning.getLastErrorCode());
        assertEquals(
                "initialAuthority.email",
                provisioning.getLastErrorField()
        );

        provisioning.startAttempt();

        assertNull(provisioning.getLastErrorCode());
        assertNull(provisioning.getLastErrorField());
        assertNull(provisioning.getLastErrorSummary());

        provisioning.fail(
                "IDENTITY_SERVICE_UNAVAILABLE",
                null,
                "Identity service is temporarily unavailable."
        );
        provisioning.succeed();

        assertNull(provisioning.getLastErrorCode());
        assertNull(provisioning.getLastErrorField());
        assertNull(provisioning.getLastErrorSummary());

        provisioning.correctAuthority(
                "Corrected",
                null,
                "Authority",
                "corrected.authority",
                "corrected@dawnrise.com",
                "9876543212"
        );

        assertEquals(1, provisioning.getRequestRevision());
    }

    @Test
    void restoreSchool_whenSchoolDoesNotExist_throwsResourceNotFoundException() {
        Long id = 99L;
        when(schoolRepository.findById(id)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                ()->schoolService.restoreSchool(id));

        assertEquals("School not found", exception.getMessage());
        verify(schoolRepository).findById(id);
        verify(schoolRepository,never()).save(any(School.class));
        verifyNoInteractions(schoolMapper);
    }

    @Test
    void getAllSchools_whenSchoolsExist_returnsSortedPageResponse() {
        // Arrange
        int page = 0;
        int size = 10;
        String sortBy = "name";
        String direction = "asc";

        Sort sort = Sort.by(Sort.Direction.ASC, sortBy);

        Pageable pageable =
                PageRequest.of(page, size, sort);

        School school1 = mock(School.class);
        School school2 = mock(School.class);

        SchoolResponse response1 = mock(SchoolResponse.class);
        SchoolResponse response2 = mock(SchoolResponse.class);

        Page<School> schoolPage = new PageImpl<>(
                List.of(school1, school2),
                pageable,
                2
        );

        SchoolStatus status = SchoolStatus.ACTIVE;

        when(schoolRepository.findAllByStatus(status, pageable))
                .thenReturn(schoolPage);

        when(schoolMapper.toResponse(school1))
                .thenReturn(response1);

        when(schoolMapper.toResponse(school2))
                .thenReturn(response2);

        // Act
        PageResponse<SchoolResponse> actualResponse =
                schoolService.getAllSchools(
                        page,
                        size,
                        sortBy,
                        direction,
                        status,
                        ""
                );

        // Assert
        assertEquals(
                List.of(response1, response2),
                actualResponse.content()
        );

        assertEquals(0, actualResponse.page());
        assertEquals(10, actualResponse.size());
        assertEquals(2, actualResponse.totalElements());
        assertEquals(1, actualResponse.totalPages());
        assertTrue(actualResponse.first());
        assertTrue(actualResponse.last());

        verify(schoolRepository).findAllByStatus(status, pageable);
        verify(schoolMapper).toResponse(school1);
        verify(schoolMapper).toResponse(school2);
  }

    @Test
    void getAllSchools_whenSearchIsProvided_returnsFilteredPage() {
        // Arrange
        int page = 0;
        int size = 10;
        String sortBy = "name";
        String direction = "desc";

        Sort sort = Sort.by(Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        School school = mock(School.class);
        SchoolResponse response = mock(SchoolResponse.class);

        Page<School> schoolPage = new PageImpl<>(
                List.of(school),
                pageable,
                1
        );

        SchoolStatus status = SchoolStatus.ACTIVE;
        String search = "public";

        when(schoolRepository.searchByStatus(status, search, pageable))
                .thenReturn(schoolPage);
        when(schoolMapper.toResponse(school)).thenReturn(response);

        // Act
        PageResponse<SchoolResponse> actualResponse =
                schoolService.getAllSchools(
                        page,
                        size,
                        sortBy,
                        direction,
                        status,
                        search
                );

        // Assert
        assertEquals(List.of(response), actualResponse.content());
        assertEquals(1, actualResponse.totalElements());
        assertEquals(1, actualResponse.totalPages());
        assertTrue(actualResponse.first());
        assertTrue(actualResponse.last());

        verify(schoolRepository).searchByStatus(status, search, pageable);
        verify(schoolMapper).toResponse(school);
    }

    @Test
    void getAllSchools_whenNoSchoolsExist_returnsEmptySortedPage() {
        // Arrange
        int page = 0;
        int size = 10;
        String sortBy = "name";
        String direction = "asc";

        Sort sort = Sort.by(Sort.Direction.ASC, sortBy);

        Pageable pageable =
                PageRequest.of(page, size, sort);

        Page<School> emptyPage = Page.empty(pageable);

        SchoolStatus status = SchoolStatus.ACTIVE;

        when(schoolRepository.findAllByStatus(status, pageable))
                .thenReturn(emptyPage);

        // Act
        PageResponse<SchoolResponse> actualResponse =
                schoolService.getAllSchools(
                        page,
                        size,
                        sortBy,
                        direction,
                        status,
                        ""
                );

        // Assert
        assertTrue(actualResponse.content().isEmpty());
        assertEquals(0, actualResponse.totalElements());
        assertEquals(0, actualResponse.totalPages());
        assertTrue(actualResponse.first());
        assertTrue(actualResponse.last());

        verify(schoolRepository).findAllByStatus(status, pageable);
        verifyNoInteractions(schoolMapper);
   }

    @Test
    void getAllSchools_whenPageIsNegative_throwsInvalidRequestException() {
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> schoolService.getAllSchools(
                        -1,
                        10,
                        "name",
                        "asc",
                        SchoolStatus.ACTIVE,
                        ""
                )
        );

        assertEquals(
                "Page number cannot be negative",
                exception.getMessage()
        );

        verifyNoInteractions(schoolRepository, schoolMapper);
    }

    @Test
    void getAllSchools_whenSizeIsInvalid_throwsInvalidRequestException() {
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> schoolService.getAllSchools(
                        0,
                        0,
                        "name",
                        "asc",
                        SchoolStatus.ACTIVE,
                        ""
                )
        );

        assertEquals(
                "Page size must be between 1 and 100",
                exception.getMessage()
        );

        verifyNoInteractions(schoolRepository, schoolMapper);
        }

    @Test
    void getAllSchools_whenSortFieldIsInvalid_throwsInvalidRequestException() {
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> schoolService.getAllSchools(
                        0,
                        10,
                        "unknownField",
                        "asc",
                        SchoolStatus.ACTIVE,
                        ""
                )
        );

        assertEquals(
                "Invalid sort field: unknownField",
                exception.getMessage()
        );

        verifyNoInteractions(schoolRepository, schoolMapper);
  }

    @Test
    void getAllSchools_whenDirectionIsInvalid_throwsInvalidRequestException() {
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> schoolService.getAllSchools(
                        0,
                        10,
                        "name",
                        "sideways",
                        SchoolStatus.ACTIVE,
                        ""
                )
        );

        assertEquals(
                "Sort direction must be asc or desc",
                exception.getMessage()
        );

        verifyNoInteractions(schoolRepository, schoolMapper);
   }

    private SchoolOnboardingRequest onboardingRequest() {
        SchoolOnboardingRequest request = mock(SchoolOnboardingRequest.class);
        InitialAuthorityRequest authority = mock(InitialAuthorityRequest.class);
        when(request.getInitialAuthority()).thenReturn(authority);
        when(authority.getFirstName()).thenReturn("Authority");
        when(authority.getMiddleName()).thenReturn(null);
        when(authority.getLastName()).thenReturn("One");
        when(authority.getUsername()).thenReturn("authority.one");
        when(authority.getEmail()).thenReturn("authority@dawnrise.com");
        when(authority.getPhone()).thenReturn("9876543211");
        return request;
    }

    private SchoolProvisioning provisioning() {
        SchoolProvisioning provisioning = mock(SchoolProvisioning.class);
        when(provisioning.getId()).thenReturn(10L);
        when(provisioning.getSchoolId()).thenReturn(1L);
        when(provisioning.getAuthorityFirstName()).thenReturn("Authority");
        when(provisioning.getAuthorityMiddleName()).thenReturn(null);
        when(provisioning.getAuthorityLastName()).thenReturn("One");
        when(provisioning.getAuthorityUsername()).thenReturn("authority.one");
        when(provisioning.getAuthorityEmail()).thenReturn("authority@dawnrise.com");
        when(provisioning.getAuthorityPhone()).thenReturn("9876543211");
        return provisioning;
    }

    private SchoolProvisioning provisioningSucceeded() {
        SchoolProvisioning provisioning = mock(SchoolProvisioning.class);
        when(provisioning.getStatus()).thenReturn(ProvisioningStatus.SUCCEEDED);
        return provisioning;
    }

    private AuthorityCorrectionRequest authorityCorrectionRequest() {
        AuthorityCorrectionRequest request =
                new AuthorityCorrectionRequest();
        request.setFirstName("Corrected");
        request.setMiddleName("Middle");
        request.setLastName("Authority");
        request.setUsername("corrected.authority");
        request.setEmail("corrected@dawnrise.com");
        request.setPhone("9876543212");
        return request;
    }

    private IdentityProvisioningResponse identitySuccessResponse() {
        return new IdentityProvisioningResponse(
                1L,
                "ACTIVE",
                20L,
                "PENDING_ACTIVATION",
                "SUCCEEDED"
        );
    }
}
