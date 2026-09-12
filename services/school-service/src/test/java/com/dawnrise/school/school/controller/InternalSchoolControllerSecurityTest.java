package com.dawnrise.school.school.controller;

import com.dawnrise.school.config.SecurityConfig;
import com.dawnrise.school.school.DTO.SchoolTimeZoneResponse;
import com.dawnrise.school.school.exception.GlobalExceptionHandler;
import com.dawnrise.school.school.service.SchoolService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalSchoolController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
        "security.internal.api-key=test-internal-key",
        "security.internal.allowed-service-names[0]=academic-service"
})
class InternalSchoolControllerSecurityTest {

    private static final String PATH =
            "/internal/v1/organizations/7/time-zone";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private SchoolService schoolService;

    @Test
    void getTimeZone_whenCredentialsAreValid_returnsTimeZone()
            throws Exception {
        when(schoolService.getSchoolTimeZone(7L))
                .thenReturn(new SchoolTimeZoneResponse(
                        7L,
                        "Asia/Kolkata"
                ));

        mockMvc.perform(get(PATH)
                        .header("X-Service-Name", "academic-service")
                        .header("X-Internal-Api-Key", "test-internal-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(7))
                .andExpect(jsonPath("$.timeZoneId").value("Asia/Kolkata"));

        verify(schoolService).getSchoolTimeZone(7L);
    }

    @Test
    void getTimeZone_whenCredentialsAreMissing_returnsUnauthorized()
            throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(content().encoding("UTF-8"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid internal service credentials"))
                .andExpect(jsonPath("$.path").value(PATH))
                .andExpect(jsonPath("$.validationErrors").doesNotExist())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "test-internal-key"
                                )
                        )));

        verifyNoInteractions(schoolService);
    }

    @Test
    void getTimeZone_whenServiceNameIsNotAllowed_returnsUnauthorized()
            throws Exception {
        mockMvc.perform(get(PATH)
                        .header("X-Service-Name", "identity-service")
                        .header("X-Internal-Api-Key", "test-internal-key"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(content().encoding("UTF-8"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message")
                        .value("Internal service is not allowed"))
                .andExpect(jsonPath("$.path").value(PATH))
                .andExpect(jsonPath("$.validationErrors").doesNotExist())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "test-internal-key"
                                )
                        )));

        verifyNoInteractions(schoolService);
    }

    @Test
    void getTimeZone_whenApiKeyIsWrong_returnsUnauthorized()
            throws Exception {
        mockMvc.perform(get(PATH)
                        .header("X-Service-Name", "academic-service")
                        .header("X-Internal-Api-Key", "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(content().encoding("UTF-8"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid internal service credentials"))
                .andExpect(jsonPath("$.path").value(PATH))
                .andExpect(jsonPath("$.validationErrors").doesNotExist())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "wrong-key"
                                )
                        )));

        verifyNoInteractions(schoolService);
    }

    @Test
    void nonGetInternalPath_whenCredentialsMissing_returnsUnauthorized()
            throws Exception {
        mockMvc.perform(post(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(content().encoding("UTF-8"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid internal service credentials"))
                .andExpect(jsonPath("$.path").value(PATH))
                .andExpect(jsonPath("$.validationErrors").doesNotExist());

        verifyNoInteractions(schoolService);
    }
}
