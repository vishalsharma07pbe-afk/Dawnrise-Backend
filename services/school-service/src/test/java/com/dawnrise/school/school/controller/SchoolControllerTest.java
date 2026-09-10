package com.dawnrise.school.school.controller;

import com.dawnrise.school.school.DTO.SchoolOnboardingRequest;
import com.dawnrise.school.school.DTO.SchoolProvisioningResponse;
import com.dawnrise.school.school.DTO.SchoolResponse;
import com.dawnrise.school.school.DTO.SchoolTimeZoneResponse;
import com.dawnrise.school.school.DTO.UpdateSchoolRequest;
import com.dawnrise.school.school.DTO.UpdateSchoolTimeZoneRequest;
import com.dawnrise.school.school.DTO.AuthorityCorrectionRequest;
import com.dawnrise.school.school.enums.ProvisioningStatus;
import com.dawnrise.school.school.enums.SchoolStatus;
import com.dawnrise.school.school.exception.DuplicateResourceException;
import com.dawnrise.school.school.exception.ResourceNotFoundException;
import com.dawnrise.school.school.exception.InvalidRequestException;
import com.dawnrise.school.school.exception.InvalidSchoolTimeZoneException;
import com.dawnrise.school.school.service.SchoolService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.dawnrise.school.common.dto.PageResponse;

import java.util.List;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SchoolController.class)
@AutoConfigureMockMvc(addFilters = false)
class SchoolControllerTest {

    private static final String BASE_URL = "/api/v1/schools";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SchoolService schoolService;

    @Test
    void createSchool_whenRequestIsValid_returnsCreatedSchool()
            throws Exception {

        String requestJson = """
                {
                  "schoolCode": "SCH001",
                  "name": "Dawnrise Public School",
                  "email": "school@dawnrise.com",
                  "phone": "9876543210",
                  "address": "New Delhi",
                  "initialAuthority": {
                    "firstName": "Authority",
                    "lastName": "One",
                    "username": "authority.one",
                    "email": "authority@dawnrise.com",
                    "phone": "9876543211"
                  }
                }
                """;

        SchoolProvisioningResponse response = createProvisioningResponse(
                1L,
                SchoolStatus.ACTIVE,
                ProvisioningStatus.SUCCEEDED,
                1,
                null
        );

        when(schoolService.onboardSchool(
                any(SchoolOnboardingRequest.class)
        )).thenReturn(response);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(content()
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.schoolId").value(1))
                .andExpect(jsonPath("$.schoolStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.provisioningStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.attemptCount").value(1));

        verify(schoolService)
                .onboardSchool(any(SchoolOnboardingRequest.class));
    }

    @Test
    void createSchool_whenRequestIsInvalid_returnsBadRequest()
            throws Exception {

        String requestJson = """
            {
              "schoolCode": "",
              "name": "",
              "email": "invalid-email",
              "phone": "123",
              "address": "New Delhi",
              "initialAuthority": {
                "firstName": "",
                "username": "",
                "email": "bad",
                "phone": "1"
              }
            }
            """;

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"));

        verifyNoInteractions(schoolService);
    }

    @Test
    void createSchool_whenCodeAlreadyExists_returnsConflict()
            throws Exception {

        String requestJson = """
                {
                  "schoolCode": "SCH001",
                  "name": "Dawnrise Public School",
                  "email": "school@dawnrise.com",
                  "phone": "9876543210",
                  "address": "New Delhi",
                  "initialAuthority": {
                    "firstName": "Authority",
                    "lastName": "One",
                    "username": "authority.one",
                    "email": "authority@dawnrise.com",
                    "phone": "9876543211"
                  }
                }
                """;

        when(schoolService.onboardSchool(
                any(SchoolOnboardingRequest.class)
        )).thenThrow(
                new DuplicateResourceException(
                        "School code already exists"
                )
        );

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("School information conflicts with an existing record."))
                .andExpect(jsonPath("$.validationErrors.schoolCode")
                        .value("This school code is already registered."))
                .andExpect(jsonPath("$.path")
                        .value(BASE_URL));

        verify(schoolService)
                .onboardSchool(any(SchoolOnboardingRequest.class));
    }

    @Test
    void getProvisioningStatus_whenSchoolExists_returnsStatus()
            throws Exception {

        when(schoolService.getProvisioningStatus(1L))
                .thenReturn(createProvisioningResponse(
                        1L,
                        SchoolStatus.PROVISIONING_FAILED,
                        ProvisioningStatus.FAILED,
                        1,
                        "identity-service unavailable"
                ));

        mockMvc.perform(get(BASE_URL + "/{schoolId}/provisioning", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolId").value(1))
                .andExpect(jsonPath("$.schoolStatus").value("PROVISIONING_FAILED"))
                .andExpect(jsonPath("$.provisioningStatus").value("FAILED"))
                .andExpect(jsonPath("$.lastErrorSummary").value("identity-service unavailable"));

        verify(schoolService).getProvisioningStatus(1L);
    }

    @Test
    void retryProvisioning_whenFailed_returnsUpdatedStatus()
            throws Exception {

        when(schoolService.retryProvisioning(1L))
                .thenReturn(createProvisioningResponse(
                        1L,
                        SchoolStatus.ACTIVE,
                        ProvisioningStatus.SUCCEEDED,
                        2,
                        null
                ));

        mockMvc.perform(post(BASE_URL + "/{schoolId}/provisioning/retry", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.provisioningStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.attemptCount").value(2));

        verify(schoolService).retryProvisioning(1L);
    }

    @Test
    void correctProvisioningAuthority_whenRequestIsValid_returnsUpdatedStatus()
            throws Exception {

        when(schoolService.correctProvisioningAuthority(
                eq(1L),
                any(AuthorityCorrectionRequest.class)
        )).thenReturn(createProvisioningResponse(
                1L,
                SchoolStatus.PROVISIONING_FAILED,
                ProvisioningStatus.FAILED,
                1,
                null
        ));

        mockMvc.perform(put(BASE_URL + "/{schoolId}/provisioning/authority", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Corrected",
                                  "middleName": "Middle",
                                  "lastName": "Authority",
                                  "username": "corrected.authority",
                                  "email": "corrected@dawnrise.com",
                                  "phone": "9876543212"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolStatus")
                        .value("PROVISIONING_FAILED"))
                .andExpect(jsonPath("$.provisioningStatus")
                        .value("FAILED"));

        verify(schoolService).correctProvisioningAuthority(
                eq(1L),
                any(AuthorityCorrectionRequest.class)
        );
    }

    @Test
    void correctProvisioningAuthority_whenRequestIsInvalid_returnsBadRequest()
            throws Exception {

        mockMvc.perform(put(BASE_URL + "/{schoolId}/provisioning/authority", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "",
                                  "username": "bad username",
                                  "email": "bad",
                                  "phone": "1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"));

        verifyNoInteractions(schoolService);
    }

    @Test
    void getSchool_whenSchoolExists_returnsSchoolResponse()
            throws Exception {

        long schoolId = 1L;

        when(schoolService.getSchoolById(schoolId))
                .thenReturn(createSchoolResponse(
                        schoolId,
                        "SCH001",
                        "Dawnrise Public School",
                        SchoolStatus.ACTIVE
                ));

        mockMvc.perform(get(
                        BASE_URL + "/{schoolId}",
                        schoolId
                ))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.schoolCode").value("SCH001"))
                .andExpect(jsonPath("$.name")
                        .value("Dawnrise Public School"))
                .andExpect(jsonPath("$.timeZoneId")
                        .value("Asia/Kolkata"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(schoolService).getSchoolById(schoolId);
    }

    @Test
    void getSchool_whenSchoolDoesNotExist_returnsNotFound()
            throws Exception {

        long schoolId = 99L;

        when(schoolService.getSchoolById(schoolId))
                .thenThrow(
                        new ResourceNotFoundException(
                                "School not found"
                        )
                );

        mockMvc.perform(get(
                        BASE_URL + "/{schoolId}",
                        schoolId
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("School not found"))
                .andExpect(jsonPath("$.path")
                        .value(BASE_URL + "/99"));

        verify(schoolService).getSchoolById(schoolId);
    }

    @Test
    void getAllSchools_whenSchoolsExist_returnsPagedResponse()
            throws Exception {

        SchoolResponse firstSchool = createSchoolResponse(
                1L,
                "SCH001",
                "Dawnrise Public School",
                SchoolStatus.ACTIVE
        );

        SchoolResponse secondSchool = createSchoolResponse(
                2L,
                "SCH002",
                "Dawnrise International School",
                SchoolStatus.ACTIVE
        );

        PageResponse<SchoolResponse> response =
                new PageResponse<>(
                        List.of(firstSchool, secondSchool),
                        0,
                        10,
                        2,
                        1,
                        true,
                        true
                );

        when(schoolService.getAllSchools(
                0,
                10,
                "name",
                "desc",
                SchoolStatus.ACTIVE,
                ""
        )).thenReturn(response);

        mockMvc.perform(get(BASE_URL)
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "name")
                        .param("direction", "desc")
                        .param("status", "ACTIVE")
                        )
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].schoolCode")
                        .value("SCH001"))
                .andExpect(jsonPath("$.content[0].status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.content[1].id").value(2))
                .andExpect(jsonPath("$.content[1].schoolCode")
                        .value("SCH002"))
                .andExpect(jsonPath("$.content[1].status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));

        verify(schoolService).getAllSchools(
                0,
                10,
                "name",
                "desc",
                SchoolStatus.ACTIVE,
                ""
        );
    }

    @Test
    void getAllSchools_whenSearchIsProvided_returnsFilteredPage()
            throws Exception {

        SchoolResponse firstSchool = createSchoolResponse(
                1L,
                "SCH001",
                "Dawnrise Public School",
                SchoolStatus.ACTIVE
        );

        PageResponse<SchoolResponse> response = new PageResponse<>(
                List.of(firstSchool),
                0,
                10,
                1,
                1,
                true,
                true
        );

        when(schoolService.getAllSchools(
                0,
                10,
                "name",
                "desc",
                SchoolStatus.ACTIVE,
                "Public"
        )).thenReturn(response);

        mockMvc.perform(get(BASE_URL)
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "name")
                        .param("direction", "desc")
                        .param("status", "ACTIVE")
                        .param("search", "Public"))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].schoolCode")
                        .value("SCH001"))
                .andExpect(jsonPath("$.content[0].status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(schoolService).getAllSchools(
                0,
                10,
                "name",
                "desc",
                SchoolStatus.ACTIVE,
                "Public"
        );
    }

    @Test
    void getAllSchools_whenNoSchoolsExist_returnsEmptyPage()
            throws Exception {

        PageResponse<SchoolResponse> response =
                new PageResponse<>(
                        List.of(),
                        0,
                        10,
                        0,
                        0,
                        true,
                        true
                );

        when(schoolService.getAllSchools(
                0,
                10,
                "name",
                "asc",
                SchoolStatus.ACTIVE,
                ""
        )).thenReturn(response);

        mockMvc.perform(get(BASE_URL)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));

        verify(schoolService).getAllSchools(
                0,
                10,
                "name",
                "asc",
                SchoolStatus.ACTIVE,
                ""
        );
    }

    @Test
    void updateSchool_whenRequestIsValid_returnsUpdatedSchool()
            throws Exception {

        long schoolId = 1L;

        String requestJson = """
                {
                  "name": "Updated Dawnrise School",
                  "email": "updated@dawnrise.com",
                  "phone": "9876543211",
                  "address": "Gurugram"
                }
                """;

        SchoolResponse response = createSchoolResponse(
                schoolId,
                "SCH001",
                "Updated Dawnrise School",
                SchoolStatus.ACTIVE
        );

        when(schoolService.updateSchool(
                eq(schoolId),
                any(UpdateSchoolRequest.class)
        )).thenReturn(response);

        mockMvc.perform(put(
                        BASE_URL + "/{schoolId}",
                        schoolId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.schoolCode").value("SCH001"))
                .andExpect(jsonPath("$.name")
                        .value("Updated Dawnrise School"))
                .andExpect(jsonPath("$.timeZoneId")
                        .value("Asia/Kolkata"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(schoolService).updateSchool(
                eq(schoolId),
                any(UpdateSchoolRequest.class)
        );
    }

    @Test
    void getCurrentTimeZone_returnsCurrentSchoolTimeZone()
            throws Exception {
        when(schoolService.getSchoolTimeZone(7L))
                .thenReturn(new SchoolTimeZoneResponse(
                        7L,
                        "Asia/Kolkata"
                ));

        mockMvc.perform(get(BASE_URL + "/current/time-zone")
                        .principal(authentication(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(7))
                .andExpect(jsonPath("$.timeZoneId").value("Asia/Kolkata"));

        verify(schoolService).getSchoolTimeZone(7L);
    }

    @Test
    void updateCurrentTimeZone_whenRequestIsValid_returnsUpdatedTimeZone()
            throws Exception {
        when(schoolService.updateSchoolTimeZone(
                eq(7L),
                any(UpdateSchoolTimeZoneRequest.class)
        )).thenReturn(new SchoolTimeZoneResponse(
                7L,
                "Europe/London"
        ));

        mockMvc.perform(put(BASE_URL + "/current/time-zone")
                        .principal(authentication(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "timeZoneId": "Europe/London"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(7))
                .andExpect(jsonPath("$.timeZoneId").value("Europe/London"));

        verify(schoolService).updateSchoolTimeZone(
                eq(7L),
                any(UpdateSchoolTimeZoneRequest.class)
        );
    }

    @Test
    void updateCurrentTimeZone_whenBlank_returnsBadRequest()
            throws Exception {
        mockMvc.perform(put(BASE_URL + "/current/time-zone")
                        .principal(authentication(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "timeZoneId": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"));

        verifyNoInteractions(schoolService);
    }

    @Test
    void updateCurrentTimeZone_whenInvalid_returnsBadRequest()
            throws Exception {
        when(schoolService.updateSchoolTimeZone(
                eq(7L),
                any(UpdateSchoolTimeZoneRequest.class)
        )).thenThrow(new InvalidSchoolTimeZoneException(
                "Time zone ID must be a valid IANA time zone"
        ));

        mockMvc.perform(put(BASE_URL + "/current/time-zone")
                        .principal(authentication(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "timeZoneId": "IST"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Time zone ID must be a valid IANA time zone"));
    }

    @Test
    void updateCurrentTimeZone_whenOverLength_returnsBadRequest()
            throws Exception {
        String overLength = "A".repeat(65);

        mockMvc.perform(put(BASE_URL + "/current/time-zone")
                        .principal(authentication(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "timeZoneId": "%s"
                                }
                                """.formatted(overLength)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"));

        verifyNoInteractions(schoolService);
    }

    @Test
    void updateSchool_whenRequestIsInvalid_returnsBadRequest()
            throws Exception {

        String requestJson = """
                {
                  "schoolCode": "",
                  "name": "",
                  "email": "invalid-email",
                  "phone": "123",
                  "address": "New Delhi"
                }
                """;

        mockMvc.perform(put(
                        BASE_URL + "/{schoolId}",
                        1L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"));

        verifyNoInteractions(schoolService);
    }

    @Test
    void updateSchool_whenSchoolDoesNotExist_returnsNotFound()
            throws Exception {

        long schoolId = 99L;

        String requestJson = """
                {
                  "name": "Updated School",
                  "email": "updated@dawnrise.com",
                  "phone": "9876543210",
                  "address": "New Delhi"
                }
                """;

        when(schoolService.updateSchool(
                eq(schoolId),
                any(UpdateSchoolRequest.class)
        )).thenThrow(
                new ResourceNotFoundException("School not found")
        );

        mockMvc.perform(put(
                        BASE_URL + "/{schoolId}",
                        schoolId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("School not found"))
                .andExpect(jsonPath("$.path")
                        .value(BASE_URL + "/99"));

        verify(schoolService).updateSchool(
                eq(schoolId),
                any(UpdateSchoolRequest.class)
        );
    }

    @Test
    void deleteSchool_whenSchoolExists_returnsNoContent()
            throws Exception {

        mockMvc.perform(delete(
                        BASE_URL + "/{schoolId}",
                        1L
                ))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(schoolService).deleteSchool(1L);
    }

    @Test
    void deleteSchool_whenSchoolDoesNotExist_returnsNotFound()
            throws Exception {

        long schoolId = 99L;

        org.mockito.Mockito.doThrow(
                new ResourceNotFoundException("School not found")
        ).when(schoolService).deleteSchool(schoolId);

        mockMvc.perform(delete(
                        BASE_URL + "/{schoolId}",
                        schoolId
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("School not found"))
                .andExpect(jsonPath("$.path")
                        .value(BASE_URL + "/99"));

        verify(schoolService).deleteSchool(schoolId);
    }

    @Test
    void restoreSchool_whenSchoolExists_returnsSchoolResponse()
            throws Exception {

        SchoolResponse response = createSchoolResponse(
                1L,
                "SCH001",
                "Dawnrise Public School",
                SchoolStatus.ACTIVE
        );

        when(schoolService.restoreSchool(1L)).thenReturn(response);

        mockMvc.perform(patch(
                        BASE_URL + "/{schoolId}/restore",
                        1L
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.schoolCode").value("SCH001"))
                .andExpect(jsonPath("$.name").value("Dawnrise Public School"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(schoolService).restoreSchool(1L);
    }

    @Test
    void restoreSchool_whenSchoolDoesNotExist_returnsNotFound()
            throws Exception {

        long schoolId = 99L;

        org.mockito.Mockito.doThrow(
                new ResourceNotFoundException("School not found")
        ).when(schoolService).restoreSchool(schoolId);

        mockMvc.perform(patch(
                        BASE_URL + "/{schoolId}/restore",
                        schoolId
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("School not found"))
                .andExpect(jsonPath("$.path")
                        .value(BASE_URL + "/99/restore"));

        verify(schoolService).restoreSchool(schoolId);
    }

    private SchoolResponse createSchoolResponse(
            long id,
            String schoolCode,
            String name,
            SchoolStatus status
    ) {
        return new SchoolResponse(
                id,
                schoolCode,
                name,
                "school@dawnrise.com",
                "9876543210",
                "New Delhi",
                "Asia/Kolkata",
                status,
                null,
                null
        );
    }

    private JwtAuthenticationToken authentication(Long organizationId) {
        Jwt jwt = Jwt.withTokenValue("organization-token")
                .header("alg", "none")
                .subject("42")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claim("organizationId", organizationId)
                .build();

        return new JwtAuthenticationToken(jwt);
    }

    private SchoolProvisioningResponse createProvisioningResponse(
            long schoolId,
            SchoolStatus schoolStatus,
            ProvisioningStatus provisioningStatus,
            int attemptCount,
            String lastErrorSummary
    ) {
        return new SchoolProvisioningResponse(
                schoolId,
                schoolStatus,
                provisioningStatus,
                attemptCount,
                lastErrorSummary,
                null,
                null
        );
    }

    @Test
    void getAllSchools_whenParametersAreInvalid_returnsBadRequest()
                throws Exception {

        when(schoolService.getAllSchools(
                0,
                10,
                "unknownField",
                "asc",
                SchoolStatus.ACTIVE,
                ""
        )).thenThrow(
                new InvalidRequestException(
                        "Invalid sort field: unknownField"
                )
        );

        mockMvc.perform(get(BASE_URL)
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "unknownField")
                        .param("direction", "asc")
                        .param("status", "ACTIVE"))
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid sort field: unknownField"))
                .andExpect(jsonPath("$.path").value(BASE_URL));

        verify(schoolService).getAllSchools(
                0,
                10,
                "unknownField",
                "asc",
                SchoolStatus.ACTIVE,
                ""
        );
    }

    @Test
    void getAllSchools_whenStatusIsInvalid_returnsBadRequest()
            throws Exception {

        mockMvc.perform(get(BASE_URL)
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "name")
                        .param("direction", "asc")
                        .param("status", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid value for status: INVALID"))
                .andExpect(jsonPath("$.path").value(BASE_URL));

        verifyNoInteractions(schoolService);
    }
}
