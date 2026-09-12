package com.dawnrise.academic.academiccalendar.controller;

import com.dawnrise.academic.academiccalendar.dto.AcademicCalendarDayResponse;
import com.dawnrise.academic.academiccalendar.dto.InitializeAcademicCalendarRequest;
import com.dawnrise.academic.academiccalendar.dto.UpdateAcademicCalendarDayRequest;
import com.dawnrise.academic.academiccalendar.enums.AttendanceRequirement;
import com.dawnrise.academic.academiccalendar.enums.CalendarDayType;
import com.dawnrise.academic.academiccalendar.service.AcademicCalendarService;
import com.dawnrise.academic.common.exception.GlobalExceptionHandler;
import com.dawnrise.academic.config.SecurityConfig;
import com.dawnrise.academic.security.AcademicTenantSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AcademicCalendarController.class)
@Import({
        SecurityConfig.class,
        AcademicTenantSecurity.class,
        GlobalExceptionHandler.class,
        AcademicCalendarControllerTest.TestConfig.class
})
class AcademicCalendarControllerTest {

    private static final String BASE =
            "/api/v1/academic-years/100/calendar-days";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubAcademicCalendarService service;

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtWithoutOrganizationIdReturns403() throws Exception {
        mockMvc.perform(get(BASE)
                        .with(jwtWithPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_VIEW"
                        )))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCollectionAndOneRequireCalendarView() throws Exception {
        service.response = response();

        mockMvc.perform(get(BASE)
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_VIEW"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(200));

        mockMvc.perform(get(BASE + "/200")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_VIEW"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(200));
    }

    @Test
    void initializeAndUpdateRequireCalendarManage() throws Exception {
        service.response = response();

        mockMvc.perform(post(BASE + "/initialize")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_MANAGE"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInitializeJson()))
                .andExpect(status().isCreated());

        mockMvc.perform(put(BASE + "/200")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_MANAGE"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateJson()))
                .andExpect(status().isOk());
    }

    @Test
    void calendarViewAloneCannotInitializeOrUpdate() throws Exception {
        mockMvc.perform(post(BASE + "/initialize")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_VIEW"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInitializeJson()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(BASE + "/200")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_VIEW"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void calendarManageAloneDoesNotGrantGet() throws Exception {
        mockMvc.perform(get(BASE)
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_MANAGE"
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(BASE + "/200")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_MANAGE"
                        )))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizationIdComesFromJwt() throws Exception {
        service.response = response();

        mockMvc.perform(get(BASE)
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_VIEW"
                        )))
                .andExpect(status().isOk());

        assertThat(service.lastGetDaysOrganizationId).isEqualTo(10L);
    }

    @Test
    void actorUserIdComesFromJwtForInitializeAndUpdate() throws Exception {
        service.response = response();

        mockMvc.perform(post(BASE + "/initialize")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_MANAGE"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInitializeJson()))
                .andExpect(status().isCreated());

        mockMvc.perform(put(BASE + "/200")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_MANAGE"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateJson()))
                .andExpect(status().isOk());

        assertThat(service.lastInitializeActorUserId).isEqualTo(20L);
        assertThat(service.lastUpdateActorUserId).isEqualTo(20L);
    }

    @Test
    void positiveAcademicYearIdAndCalendarDayIdValidation() throws Exception {
        mockMvc.perform(get("/api/v1/academic-years/0/calendar-days")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_VIEW"
                        )))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(BASE + "/0")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_VIEW"
                        )))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidRequestBodiesReturn400() throws Exception {
        mockMvc.perform(post(BASE + "/initialize")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_MANAGE"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workingWeekDays": [],
                                  "defaultWorkingDayWeight": 0
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(BASE + "/200")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_MANAGE"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void successfulInitializeReturns201() throws Exception {
        service.response = response();

        mockMvc.perform(post(BASE + "/initialize")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_MANAGE"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInitializeJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(200));
    }

    @Test
    void successfulGetAndPutReturn200() throws Exception {
        service.response = response();

        mockMvc.perform(get(BASE)
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_VIEW"
                        )))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/200")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_VIEW"
                        )))
                .andExpect(status().isOk());

        mockMvc.perform(put(BASE + "/200")
                        .with(jwtWithOrganizationUserAndPermission(
                                "STUDENT_ATTENDANCE_CALENDAR_MANAGE"
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(200));
    }

    private static RequestPostProcessor jwtWithOrganizationUserAndPermission(
            String permission
    ) {
        return jwt()
                .jwt(jwt -> jwt
                        .claim("organizationId", 10L)
                        .subject("20"))
                .authorities(() -> permission);
    }

    private static RequestPostProcessor jwtWithPermission(String permission) {
        return jwt().authorities(() -> permission);
    }

    private static String validInitializeJson() {
        return """
                {
                  "workingWeekDays": ["MONDAY", "TUESDAY"],
                  "defaultWorkingDayWeight": 1.00
                }
                """;
    }

    private static String validUpdateJson() {
        return """
                {
                  "dayType": "WORKING_DAY",
                  "name": "Regular day",
                  "attendanceRequirement": "REQUIRED",
                  "countsTowardPercentage": true,
                  "dayWeight": 1.00,
                  "note": "Updated",
                  "expectedVersion": 0
                }
                """;
    }

    private static AcademicCalendarDayResponse response() {
        return new AcademicCalendarDayResponse(
                200L,
                100L,
                LocalDate.parse("2026-04-01"),
                CalendarDayType.WORKING_DAY,
                "Regular day",
                AttendanceRequirement.REQUIRED,
                true,
                new BigDecimal("1.00"),
                "Updated",
                0L,
                OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubAcademicCalendarService academicCalendarService() {
            return new StubAcademicCalendarService();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("20")
                    .build();
        }
    }

    static class StubAcademicCalendarService
            implements AcademicCalendarService {

        private AcademicCalendarDayResponse response;
        private long lastInitializeActorUserId;
        private long lastGetDaysOrganizationId;
        private long lastUpdateActorUserId;

        @Override
        public List<AcademicCalendarDayResponse> initialize(
                long organizationId,
                long academicYearId,
                long actorUserId,
                InitializeAcademicCalendarRequest request
        ) {
            lastInitializeActorUserId = actorUserId;
            return List.of(response);
        }

        @Override
        public List<AcademicCalendarDayResponse> getDays(
                long organizationId,
                long academicYearId,
                LocalDate startDate,
                LocalDate endDate
        ) {
            lastGetDaysOrganizationId = organizationId;
            return List.of(response);
        }

        @Override
        public AcademicCalendarDayResponse getDay(
                long organizationId,
                long academicYearId,
                long calendarDayId
        ) {
            return response;
        }

        @Override
        public AcademicCalendarDayResponse updateDay(
                long organizationId,
                long academicYearId,
                long calendarDayId,
                long actorUserId,
                UpdateAcademicCalendarDayRequest request
        ) {
            lastUpdateActorUserId = actorUserId;
            return response;
        }
    }
}
