package com.edusphere.school.school.controller;

import com.edusphere.school.school.DTO.SchoolOnboardingRequest;
import com.edusphere.school.school.DTO.SchoolProvisioningResponse;
import com.edusphere.school.school.DTO.SchoolResponse;
import com.edusphere.school.school.DTO.UpdateSchoolRequest;
import com.edusphere.school.school.enums.ProvisioningStatus;
import com.edusphere.school.school.enums.SchoolStatus;
import com.edusphere.school.school.exception.DuplicateResourceException;
import com.edusphere.school.school.exception.ResourceNotFoundException;
import com.edusphere.school.school.exception.InvalidRequestException;
import com.edusphere.school.school.service.SchoolService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.edusphere.school.common.dto.PageResponse;

import java.util.List;

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
                  "name": "EduSphere Public School",
                  "email": "school@edusphere.com",
                  "phone": "9876543210",
                  "address": "New Delhi",
                  "initialAuthority": {
                    "firstName": "Authority",
                    "lastName": "One",
                    "username": "authority.one",
                    "email": "authority@edusphere.com",
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
                  "name": "EduSphere Public School",
                  "email": "school@edusphere.com",
                  "phone": "9876543210",
                  "address": "New Delhi",
                  "initialAuthority": {
                    "firstName": "Authority",
                    "lastName": "One",
                    "username": "authority.one",
                    "email": "authority@edusphere.com",
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
                        .value("School code already exists"))
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
    void getSchool_whenSchoolExists_returnsSchoolResponse()
            throws Exception {

        long schoolId = 1L;

        when(schoolService.getSchoolById(schoolId))
                .thenReturn(createSchoolResponse(
                        schoolId,
                        "SCH001",
                        "EduSphere Public School",
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
                        .value("EduSphere Public School"))
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
                "EduSphere Public School",
                SchoolStatus.ACTIVE
        );

        SchoolResponse secondSchool = createSchoolResponse(
                2L,
                "SCH002",
                "EduSphere International School",
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
                "EduSphere Public School",
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
                  "name": "Updated EduSphere School",
                  "email": "updated@edusphere.com",
                  "phone": "9876543211",
                  "address": "Gurugram"
                }
                """;

        SchoolResponse response = createSchoolResponse(
                schoolId,
                "SCH001",
                "Updated EduSphere School",
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
                        .value("Updated EduSphere School"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(schoolService).updateSchool(
                eq(schoolId),
                any(UpdateSchoolRequest.class)
        );
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
                  "email": "updated@edusphere.com",
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
                "EduSphere Public School",
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
                .andExpect(jsonPath("$.name").value("EduSphere Public School"))
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
                "school@edusphere.com",
                "9876543210",
                "New Delhi",
                status,
                null,
                null
        );
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
