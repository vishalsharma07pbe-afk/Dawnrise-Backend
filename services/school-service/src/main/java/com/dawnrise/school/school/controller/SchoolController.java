package com.dawnrise.school.school.controller;

import com.dawnrise.school.common.dto.PageResponse;
import com.dawnrise.school.school.DTO.AuthorityCorrectionRequest;
import com.dawnrise.school.school.DTO.SchoolOnboardingRequest;
import com.dawnrise.school.school.DTO.SchoolProvisioningResponse;
import com.dawnrise.school.school.DTO.SchoolResponse;
import com.dawnrise.school.school.DTO.UpdateSchoolRequest;
import com.dawnrise.school.school.DTO.SchoolBrandingResponse;
import com.dawnrise.school.school.DTO.SchoolLogoResponse;
import com.dawnrise.school.school.enums.SchoolStatus;
import com.dawnrise.school.school.exception.InvalidRequestException;
import com.dawnrise.school.school.service.SchoolService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;


@RestController
@RequestMapping("/api/v1/schools")
public class SchoolController {
    private static final long MAX_LOGO_BYTES = 2 * 1024 * 1024;
    private final SchoolService schoolService;

    public SchoolController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @PreAuthorize(
            "@platformTokenSecurity.hasPermissions(" +
                    "authentication, " +
                    "'ORGANIZATION_CREATE', " +
                    "'PROVISIONING_START')"
    )
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SchoolProvisioningResponse> createSchool(
            @Valid @RequestBody SchoolOnboardingRequest request) {
        SchoolProvisioningResponse response = schoolService.onboardSchool(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize(
            "@platformTokenSecurity.hasPermissions(" +
                    "authentication, " +
                    "'ORGANIZATION_CREATE', " +
                    "'PROVISIONING_START')"
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SchoolProvisioningResponse> createSchoolWithBranding(
            @Valid @RequestPart("school") SchoolOnboardingRequest request,
            @RequestPart(value = "logo", required = false) MultipartFile logo
    ) throws IOException {
        byte[] logoData = null;
        String logoContentType = null;
        if (logo != null && !logo.isEmpty()) {
            if (logo.getSize() > MAX_LOGO_BYTES) {
                throw new InvalidRequestException("School logo cannot exceed 2 MB");
            }
            logoData = logo.getBytes();
            logoContentType = detectLogoContentType(logoData);
        }

        SchoolProvisioningResponse response = schoolService.onboardSchool(
                request,
                logoData,
                logoContentType
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("@schoolTenantSecurity.hasOrganization(authentication)")
    @GetMapping("/current/branding")
    public ResponseEntity<SchoolBrandingResponse> getCurrentSchoolBranding(
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(schoolService.getSchoolBranding(organizationId(authentication)));
    }

    @PreAuthorize("@schoolTenantSecurity.hasOrganization(authentication)")
    @GetMapping("/current/logo")
    public ResponseEntity<byte[]> getCurrentSchoolLogo(
            JwtAuthenticationToken authentication
    ) {
        SchoolLogoResponse logo = schoolService.getSchoolLogo(organizationId(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .cacheControl(org.springframework.http.CacheControl.noCache())
                .body(logo.data());
    }

    @PreAuthorize(
            "@platformTokenSecurity.hasPermissions(" +
                    "authentication, " +
                    "'PROVISIONING_VIEW')"
    )
    @GetMapping("/{schoolId}/provisioning")
    public ResponseEntity<SchoolProvisioningResponse> getProvisioningStatus(
            @PathVariable Long schoolId) {
        return ResponseEntity.ok(schoolService.getProvisioningStatus(schoolId));
    }

    @PreAuthorize(
            "@platformTokenSecurity.hasPermissions(" +
                    "authentication, " +
                    "'PROVISIONING_RETRY')"
    )
    @PostMapping("/{schoolId}/provisioning/retry")
    public ResponseEntity<SchoolProvisioningResponse> retryProvisioning(
            @PathVariable Long schoolId) {
        return ResponseEntity.ok(schoolService.retryProvisioning(schoolId));
    }

    @PreAuthorize(
            "@platformTokenSecurity.hasPermissions(" +
                    "authentication, " +
                    "'PROVISIONING_UPDATE')"
    )
    @PutMapping("/{schoolId}/provisioning/authority")
    public ResponseEntity<SchoolProvisioningResponse>
        correctProvisioningAuthority(
            @PathVariable Long schoolId,
            @Valid @RequestBody AuthorityCorrectionRequest request
    ) {
        return ResponseEntity.ok(
                schoolService.correctProvisioningAuthority(
                        schoolId,
                        request
                )
        );
    }

    @PreAuthorize(
            "@platformTokenSecurity.hasPermissions(" +
                    "authentication, " +
                    "'ORGANIZATION_VIEW')"
    )
    @GetMapping("/{schoolId}")
    public ResponseEntity<SchoolResponse> getSchool(
            @PathVariable Long schoolId){
        SchoolResponse response = schoolService.getSchoolById(schoolId);
        return ResponseEntity.ok().body(response);
    }

    @PreAuthorize(
            "@platformTokenSecurity.hasPermissions(" +
                    "authentication, " +
                    "'ORGANIZATION_VIEW')"
    )
    @GetMapping
    public ResponseEntity<PageResponse<SchoolResponse>> getAllSchools(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "ACTIVE") SchoolStatus status,
            @RequestParam(defaultValue = "") String search
    ) {
        PageResponse<SchoolResponse> response =
                schoolService.getAllSchools(page, size, sortBy, direction, status, search);

        return ResponseEntity.ok(response);
    }

    @PreAuthorize(
            "@platformTokenSecurity.hasPermissions(" +
                    "authentication, " +
                    "'ORGANIZATION_UPDATE')"
    )
    @PutMapping("/{schoolId}")
    public ResponseEntity<SchoolResponse> updateSchool(
            @PathVariable Long schoolId, @Valid @RequestBody UpdateSchoolRequest request){
        SchoolResponse response = schoolService.updateSchool(schoolId, request);
        return ResponseEntity.ok().body(response);
    }

    @PreAuthorize(
            "@platformTokenSecurity.hasPermissions(" +
                    "authentication, " +
                    "'ORGANIZATION_STATUS_MANAGE')"
    )
    @DeleteMapping("/{schoolId}")
    public ResponseEntity<Void> deleteSchool(@PathVariable Long schoolId){
        schoolService.deleteSchool(schoolId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize(
            "@platformTokenSecurity.hasPermissions(" +
                    "authentication, " +
                    "'ORGANIZATION_STATUS_MANAGE')"
    )
    @PatchMapping("/{schoolId}/restore")
    public ResponseEntity<SchoolResponse> restoreSchool(@PathVariable Long schoolId){
        SchoolResponse response = schoolService.restoreSchool(schoolId);
        return ResponseEntity.ok().body(response);
    }

    private static long organizationId(JwtAuthenticationToken authentication) {
        Number organizationId = authentication.getToken().getClaim("organizationId");
        return organizationId.longValue();
    }

    private static String detectLogoContentType(byte[] data) {
        if (data.length >= 8
                && (data[0] & 0xff) == 0x89
                && data[1] == 0x50 && data[2] == 0x4e && data[3] == 0x47
                && data[4] == 0x0d && data[5] == 0x0a && data[6] == 0x1a && data[7] == 0x0a) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (data.length >= 3
                && (data[0] & 0xff) == 0xff
                && (data[1] & 0xff) == 0xd8
                && (data[2] & 0xff) == 0xff) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if (data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        throw new InvalidRequestException("School logo must be a PNG, JPG, JPEG, or WebP image");
    }
}
