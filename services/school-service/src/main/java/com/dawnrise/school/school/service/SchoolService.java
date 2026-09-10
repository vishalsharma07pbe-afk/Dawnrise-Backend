package com.dawnrise.school.school.service;

import com.dawnrise.school.common.dto.PageResponse;
import com.dawnrise.school.school.DTO.AuthorityCorrectionRequest;
import com.dawnrise.school.school.DTO.SchoolOnboardingRequest;
import com.dawnrise.school.school.DTO.SchoolProvisioningResponse;
import com.dawnrise.school.school.DTO.SchoolResponse;
import com.dawnrise.school.school.DTO.UpdateSchoolRequest;
import com.dawnrise.school.school.enums.SchoolStatus;
import com.dawnrise.school.school.DTO.SchoolBrandingResponse;
import com.dawnrise.school.school.DTO.SchoolLogoResponse;
import com.dawnrise.school.school.DTO.SchoolTimeZoneResponse;
import com.dawnrise.school.school.DTO.UpdateSchoolTimeZoneRequest;

public interface SchoolService {
    SchoolResponse getSchoolById(long schoolId);
    SchoolBrandingResponse getSchoolBranding(long schoolId);
    SchoolLogoResponse getSchoolLogo(long schoolId);
    SchoolTimeZoneResponse getSchoolTimeZone(long organizationId);
    SchoolTimeZoneResponse updateSchoolTimeZone(
            long organizationId,
            UpdateSchoolTimeZoneRequest request
    );
    SchoolProvisioningResponse onboardSchool(SchoolOnboardingRequest request);
    SchoolProvisioningResponse onboardSchool(
            SchoolOnboardingRequest request,
            byte[] logoData,
            String logoContentType
    );
    SchoolProvisioningResponse getProvisioningStatus(long schoolId);
    SchoolProvisioningResponse retryProvisioning(long schoolId);
    SchoolProvisioningResponse correctProvisioningAuthority(
            long schoolId,
            AuthorityCorrectionRequest request
    );
    SchoolResponse updateSchool(long schoolId, UpdateSchoolRequest request);
    void deleteSchool(long schoolId);
    SchoolResponse restoreSchool(long schoolId);
    PageResponse<SchoolResponse> getAllSchools(
        int page, 
        int size,
        String sortBy, 
        String direction, 
        SchoolStatus status,
        String search);
}
