package com.dawnrise.school.school.service;

import com.dawnrise.school.common.dto.PageResponse;
import com.dawnrise.school.school.DTO.AuthorityCorrectionRequest;
import com.dawnrise.school.school.DTO.InitialAuthorityRequest;
import com.dawnrise.school.school.DTO.SchoolOnboardingRequest;
import com.dawnrise.school.school.DTO.SchoolProvisioningResponse;
import com.dawnrise.school.school.DTO.SchoolResponse;
import com.dawnrise.school.school.DTO.SchoolBrandingResponse;
import com.dawnrise.school.school.DTO.SchoolLogoResponse;
import com.dawnrise.school.school.DTO.SchoolTimeZoneResponse;
import com.dawnrise.school.school.DTO.UpdateSchoolRequest;
import com.dawnrise.school.school.DTO.UpdateSchoolTimeZoneRequest;
import com.dawnrise.school.school.config.SchoolProperties;
import com.dawnrise.school.school.entity.School;
import com.dawnrise.school.school.entity.SchoolProvisioning;
import com.dawnrise.school.school.enums.ProvisioningStatus;
import com.dawnrise.school.school.enums.SchoolStatus;
import com.dawnrise.school.school.exception.DuplicateResourceException;
import com.dawnrise.school.school.exception.InvalidRequestException;
import com.dawnrise.school.school.exception.ResourceNotFoundException;
import com.dawnrise.school.school.provisioning.IdentityInitialAuthorityRequest;
import com.dawnrise.school.school.provisioning.IdentityProvisioningClient;
import com.dawnrise.school.school.provisioning.IdentityProvisioningRequest;
import com.dawnrise.school.school.mapper.SchoolMapper;
import com.dawnrise.school.school.phone.PhoneNumberNormalizer;
import com.dawnrise.school.school.repository.SchoolProvisioningRepository;
import com.dawnrise.school.school.repository.schoolRepository;
import com.dawnrise.school.school.provisioning.IdentityProvisioningException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.dawnrise.school.school.provisioning.IdentityProvisioningResponse;
import java.util.List;
import org.springframework.data.domain.Sort;
import java.util.Set;

@Service
public class SchoolServiceImpl implements SchoolService {
    private static final Set<String> ALLOWED_SORT_FIELDS =
        Set.of(
                "name",
                "schoolCode",
                "createdAt",
                "updatedAt"
        );


    private final schoolRepository schoolRepository;
    private final SchoolProvisioningRepository provisioningRepository;
    private final SchoolMapper schoolMapper;
    private final IdentityProvisioningClient identityProvisioningClient;
    private final TransactionTemplate transactionTemplate;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final SchoolProperties schoolProperties;

    public SchoolServiceImpl(
            schoolRepository schoolRepository,
            SchoolProvisioningRepository provisioningRepository,
            SchoolMapper schoolMapper,
            IdentityProvisioningClient identityProvisioningClient,
            TransactionTemplate transactionTemplate,
            PhoneNumberNormalizer phoneNumberNormalizer,
            SchoolProperties schoolProperties
    ) {
        this.schoolRepository = schoolRepository;
        this.provisioningRepository = provisioningRepository;
        this.schoolMapper = schoolMapper;
        this.identityProvisioningClient = identityProvisioningClient;
        this.transactionTemplate = transactionTemplate;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.schoolProperties = schoolProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolResponse getSchoolById(long schoolId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(()->new ResourceNotFoundException("School not found"));
        return schoolMapper.toResponse(school);
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolBrandingResponse getSchoolBranding(long schoolId) {
        School school = findSchool(schoolId);
        return new SchoolBrandingResponse(
                school.getId(),
                school.getName(),
                school.getMotto(),
                school.getTagline(),
                school.getLogoData() != null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolLogoResponse getSchoolLogo(long schoolId) {
        School school = findSchool(schoolId);
        if (school.getLogoData() == null || school.getLogoContentType() == null) {
            throw new ResourceNotFoundException("School logo not found");
        }
        return new SchoolLogoResponse(school.getLogoData(), school.getLogoContentType());
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolTimeZoneResponse getSchoolTimeZone(long organizationId) {
        School school = findSchool(organizationId);
        return new SchoolTimeZoneResponse(
                school.getId(),
                school.getTimeZoneId()
        );
    }

    @Override
    @Transactional
    public SchoolTimeZoneResponse updateSchoolTimeZone(
            long organizationId,
            UpdateSchoolTimeZoneRequest request
    ) {
        School school = findSchool(organizationId);
        school.applyTimeZone(request.timeZoneId());
        School savedSchool = schoolRepository.save(school);
        return new SchoolTimeZoneResponse(
                savedSchool.getId(),
                savedSchool.getTimeZoneId()
        );
    }

    @Override
    public SchoolProvisioningResponse onboardSchool(SchoolOnboardingRequest request) {
        return onboardSchool(request, null, null);
    }

    @Override
    public SchoolProvisioningResponse onboardSchool(
            SchoolOnboardingRequest request,
            byte[] logoData,
            String logoContentType
    ) {
        if(schoolRepository.existsBySchoolCode(request.getSchoolCode())) {
            throw new DuplicateResourceException("School code already exists");
        }

        String normalizedSchoolPhone =
                phoneNumberNormalizer.normalizeRequired(
                        "phone",
                        request.getPhone()
                );
        String normalizedAuthorityPhone =
                phoneNumberNormalizer.normalizeRequired(
                        "initialAuthority.phone",
                        request.getInitialAuthority().getPhone()
                );

        Long schoolId = transactionTemplate.execute(status -> {
            School school = schoolMapper.toEntity(request);
            school.applyTimeZone(
                    schoolProperties.getDefaultTimeZone()
            );
            school.setPhone(normalizedSchoolPhone);
            school.setLogoData(logoData);
            school.setLogoContentType(logoContentType);
            School savedSchool = schoolRepository.save(school);
            InitialAuthorityRequest authority = request.getInitialAuthority();
            provisioningRepository.save(new SchoolProvisioning(
                    savedSchool.getId(),
                    authority.getFirstName(),
                    authority.getMiddleName(),
                    authority.getLastName(),
                    authority.getUsername(),
                    authority.getEmail(),
                    normalizedAuthorityPhone
            ));
            return savedSchool.getId();
        });

        runProvisioningAttempt(schoolId);
        return getProvisioningStatus(schoolId);
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolProvisioningResponse getProvisioningStatus(long schoolId) {
        School school = findSchool(schoolId);
        SchoolProvisioning provisioning = provisioningRepository.findBySchoolId(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School provisioning not found"));
        return toProvisioningResponse(school, provisioning);
    }

    @Override
    public SchoolProvisioningResponse retryProvisioning(long schoolId) {
        SchoolProvisioning provisioning = provisioningRepository.findBySchoolId(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School provisioning not found"));
        if (provisioning.getStatus() != ProvisioningStatus.FAILED) {
            throw new InvalidRequestException("Only failed provisioning can be retried");
        }
        runProvisioningAttempt(schoolId);
        return getProvisioningStatus(schoolId);
    }

    @Override
    public SchoolProvisioningResponse correctProvisioningAuthority(
            long schoolId,
            AuthorityCorrectionRequest request
    ) {
        String normalizedAuthorityPhone =
                phoneNumberNormalizer.normalizeRequired(
                        "initialAuthority.phone",
                        request.getPhone()
                );

        transactionTemplate.executeWithoutResult(status -> {
            School school = findSchool(schoolId);
            SchoolProvisioning provisioning =
                    provisioningRepository
                            .findWithLockBySchoolId(schoolId)
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "School provisioning not found"
                                    )
                            );

            if (provisioning.getStatus() == ProvisioningStatus.PENDING) {
                throw new InvalidRequestException(
                        "School provisioning is already in progress"
                );
            }

            if (provisioning.getStatus() == ProvisioningStatus.SUCCEEDED) {
                throw new InvalidRequestException(
                        "School provisioning already succeeded"
                );
            }

            provisioning.correctAuthority(
                    request.getFirstName(),
                    request.getMiddleName(),
                    request.getLastName(),
                    request.getUsername(),
                    request.getEmail(),
                    normalizedAuthorityPhone
            );
            school.markProvisioningFailed();
        });

        return getProvisioningStatus(schoolId);
    }

    @Override
    @Transactional
    public SchoolResponse updateSchool(long schoolId, UpdateSchoolRequest request) {
        String normalizedPhone =
                phoneNumberNormalizer.normalizeRequired(
                        "phone",
                        request.getPhone()
                );
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(()->new ResourceNotFoundException("School not found"));
        schoolMapper.updateEntity(request, school);
        school.setPhone(normalizedPhone);
        School updatedSchool = schoolRepository.save(school);
        return schoolMapper.toResponse(updatedSchool);
    }

    @Override
    @Transactional
    public void deleteSchool(long schoolId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(()->new ResourceNotFoundException("School not found"));
        school.deactivate();
        schoolRepository.save(school);
    }

    @Override
    @Transactional
    public SchoolResponse restoreSchool(long schoolId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(()->new ResourceNotFoundException("School not found"));
        SchoolProvisioning provisioning = provisioningRepository.findBySchoolId(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School provisioning not found"));
        if (provisioning.getStatus() != ProvisioningStatus.SUCCEEDED) {
            throw new InvalidRequestException("School identity provisioning has not succeeded");
        }
        school.activate();
        schoolRepository.save(school);
        return schoolMapper.toResponse(school);
    }

    private void runProvisioningAttempt(Long schoolId) {
        SchoolProvisioning provisioning =
                beginAttempt(schoolId);

        try {
            IdentityProvisioningResponse identityResponse =
                    identityProvisioningClient
                            .provisionInitialAuthority(
                                    toIdentityRequest(provisioning),
                                    idempotencyKey(provisioning)
                            );

            validateIdentityResponse(
                    schoolId,
                    identityResponse
            );

            completeAttempt(schoolId, null);
        } catch (RuntimeException exception) {
            completeAttempt(
                    schoolId,
                    toProvisioningFailure(exception)
            );
        }
    }

    private SchoolProvisioning beginAttempt(Long schoolId) {
        return transactionTemplate.execute(status -> {
            School school = findSchool(schoolId);
            SchoolProvisioning provisioning = provisioningRepository.findWithLockBySchoolId(schoolId)
                    .orElseThrow(() -> new ResourceNotFoundException("School provisioning not found"));
            if (provisioning.getStatus() == ProvisioningStatus.SUCCEEDED) {
                throw new InvalidRequestException("School provisioning already succeeded");
            }
            if (provisioning.getStatus() == ProvisioningStatus.PENDING && provisioning.getAttemptCount() > 0) {
                throw new InvalidRequestException("School provisioning is already in progress");
            }
            provisioning.startAttempt();
            school.markProvisioningPending();
            return provisioning;
        });
    }

    private void completeAttempt(
            Long schoolId,
            ProvisioningFailure failure
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            School school = findSchool(schoolId);

            SchoolProvisioning provisioning =
                    provisioningRepository
                            .findWithLockBySchoolId(schoolId)
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "School provisioning not found"
                                    )
                            );

            if (failure == null) {
                provisioning.succeed();
                school.activate();
            } else {
                provisioning.fail(
                        failure.code(),
                        failure.field(),
                        failure.message()
                );

                school.markProvisioningFailed();
            }
        });
    }

    private IdentityProvisioningRequest toIdentityRequest(SchoolProvisioning provisioning) {
        School school = findSchool(provisioning.getSchoolId());
        return new IdentityProvisioningRequest(
                provisioning.getSchoolId(),
                school.getSchoolCode(),
                school.getName(),
                school.getEmail(),
                new IdentityInitialAuthorityRequest(
                        provisioning.getAuthorityUsername(),
                        provisioning.getAuthorityFirstName(),
                        provisioning.getAuthorityMiddleName(),
                        provisioning.getAuthorityLastName(),
                        provisioning.getAuthorityEmail(),
                        provisioning.getAuthorityPhone()
                )
        );
    }

    private String idempotencyKey(SchoolProvisioning provisioning) {
        String legacyKey = "school-provisioning:"
                + provisioning.getSchoolId()
                + ":"
                + provisioning.getId();

        if (provisioning.getRequestRevision() == 0) {
            return legacyKey;
        }

        return legacyKey
                + ":revision:"
                + provisioning.getRequestRevision();
    }

    private ProvisioningFailure toProvisioningFailure(
            RuntimeException exception
    ) {
        if (exception instanceof IdentityProvisioningException
                identityException) {

            String errorCode = switch (
                    identityException.getStatusCode()
                    ) {
                case 400 -> "IDENTITY_VALIDATION_FAILED";
                case 409 -> "IDENTITY_CONFLICT";
                case 401, 403 -> "IDENTITY_AUTHORIZATION_FAILED";
                default -> identityException.getStatusCode() >= 500
                        ? "IDENTITY_SERVICE_UNAVAILABLE"
                        : "IDENTITY_REQUEST_FAILED";
            };

            var firstFieldError = identityException
                    .getFieldErrors()
                    .entrySet()
                    .stream()
                    .findFirst()
                    .orElse(null);

            if (firstFieldError != null) {
                return new ProvisioningFailure(
                        errorCode,
                        toFrontendField(firstFieldError.getKey()),
                        firstFieldError.getValue()
                );
            }

            String safeMessage =
                    identityException.getStatusCode() >= 500
                            ? "Identity service is temporarily unavailable."
                            : identityException.getMessage();

            return new ProvisioningFailure(
                    errorCode,
                    null,
                    safeMessage
            );
        }

        return new ProvisioningFailure(
                "IDENTITY_SERVICE_ERROR",
                null,
                "Identity provisioning could not be completed."
        );
    }

    private School findSchool(long schoolId) {
        return schoolRepository.findById(schoolId)
                .orElseThrow(()->new ResourceNotFoundException("School not found"));
    }

    private SchoolProvisioningResponse toProvisioningResponse(
            School school,
            SchoolProvisioning provisioning
    ) {
        return new SchoolProvisioningResponse(
                school.getId(),
                school.getStatus(),
                provisioning.getStatus(),
                provisioning.getAttemptCount(),
                provisioning.getLastErrorCode(),
                provisioning.getLastErrorField(),
                provisioning.getLastErrorSummary(),
                provisioning.getCreatedAt(),
                provisioning.getUpdatedAt()
        );
    }


    @Override
    @Transactional(readOnly = true)
    public PageResponse<SchoolResponse> getAllSchools(
            int page,
            int size,
            String sortBy,
            String direction,
            SchoolStatus status,
            String search
        ){
        if (page < 0) {
        throw new InvalidRequestException(
                "Page number cannot be negative"
        );
        }

        if (size < 1 || size > 100) {
            throw new InvalidRequestException(
                    "Page size must be between 1 and 100"
            );
        }

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new InvalidRequestException(
                    "Invalid sort field: " + sortBy
            );
        }

        Sort.Direction sortDirection;

        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(
                    "Sort direction must be asc or desc"
            );
        }
        Sort sort = Sort.by(sortDirection, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        String normalizedSearch =
        search == null ? "" : search.trim();

        Page<School> schoolPage;

        if(normalizedSearch.isBlank()) {
            schoolPage = schoolRepository.findAllByStatus(status, pageable);
        } else {
            schoolPage = schoolRepository.searchByStatus(status, normalizedSearch, pageable);
        }

        List<SchoolResponse> schoolResponses =
                schoolPage.getContent()
                        .stream()
                        .map(schoolMapper::toResponse)
                        .toList();

        return new PageResponse<>(
                schoolResponses,
                schoolPage.getNumber(),
                schoolPage.getSize(),
                schoolPage.getTotalElements(),
                schoolPage.getTotalPages(),
                schoolPage.isFirst(),
                schoolPage.isLast()
        );
    }

    private void validateIdentityResponse(
            Long expectedSchoolId,
            IdentityProvisioningResponse response
    ) {
        if (!expectedSchoolId.equals(
                response.organizationId()
        )) {
            throw new IllegalStateException(
                    "Identity-service returned an unexpected organization ID"
            );
        }

        if (!"ACTIVE".equals(
                response.organizationStatus()
        )) {
            throw new IllegalStateException(
                    "Identity organization is not active"
            );
        }

        if (response.authorityUserId() == null) {
            throw new IllegalStateException(
                    "Identity-service did not return an authority user ID"
            );
        }

        if (!"PENDING_ACTIVATION".equals(
                response.authorityStatus()
        )) {
            throw new IllegalStateException(
                    "Initial authority has an unexpected status"
            );
        }

        if (!"SUCCEEDED".equals(
                response.provisioningStatus()
        )) {
            throw new IllegalStateException(
                    "Identity provisioning did not succeed"
            );
        }
    }

    private String toFrontendField(String identityField) {
        if (identityField == null || identityField.isBlank()) {
            return null;
        }

        if (identityField.startsWith("authority.")) {
            return "initialAuthority."
                    + identityField.substring("authority.".length());
        }

        return identityField;
    }

    private record ProvisioningFailure(
            String code,
            String field,
            String message
    ) {
    }
}
