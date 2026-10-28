package com.dawnrise.academic.timetable.controller;

import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.*;
import com.dawnrise.academic.timetable.service.TimetableConfigurationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/timetable-configuration")
public class TimetableConfigurationController {
    private static final String VIEW = "@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('TIMETABLE_CONFIG_VIEW')";
    private static final String MANAGE = "@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('TIMETABLE_CONFIG_MANAGE')";
    private final TimetableConfigurationService service;

    public TimetableConfigurationController(TimetableConfigurationService service) { this.service = service; }

    @GetMapping("/wings") @PreAuthorize(VIEW)
    public List<WingResponse> wings(JwtAuthenticationToken auth) { return service.wings(org(auth)); }

    @PostMapping("/wings") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(MANAGE)
    public WingResponse createWing(@Valid @RequestBody WingRequest request, JwtAuthenticationToken auth) {
        return service.createWing(org(auth), actor(auth), request);
    }

    @PutMapping("/wings/{id}") @PreAuthorize(MANAGE)
    public WingResponse updateWing(@PathVariable @Positive long id, @Valid @RequestBody WingRequest request,
                                   JwtAuthenticationToken auth) {
        return service.updateWing(org(auth), actor(auth), id, request);
    }

    @PutMapping("/grade-wings") @PreAuthorize(MANAGE)
    public GradeWingResponse assignGradeWing(@Valid @RequestBody GradeWingRequest request, JwtAuthenticationToken auth) {
        return service.assignGradeWing(org(auth), actor(auth), request);
    }

    @GetMapping("/bell-schedules") @PreAuthorize(VIEW)
    public List<BellScheduleResponse> bellSchedules(JwtAuthenticationToken auth) { return service.bellSchedules(org(auth)); }

    @PostMapping("/bell-schedules") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(MANAGE)
    public BellScheduleResponse createBellSchedule(@Valid @RequestBody BellScheduleRequest request,
                                                   JwtAuthenticationToken auth) {
        return service.createBellSchedule(org(auth), actor(auth), request);
    }

    @PutMapping("/bell-schedules/{id}") @PreAuthorize(MANAGE)
    public BellScheduleResponse updateBellSchedule(@PathVariable @Positive long id,
                                                   @Valid @RequestBody BellScheduleRequest request,
                                                   JwtAuthenticationToken auth) {
        return service.updateBellSchedule(org(auth), actor(auth), id, request);
    }

    @PutMapping("/schedule-assignments") @PreAuthorize(MANAGE)
    public ScheduleAssignmentResponse assignSchedule(@Valid @RequestBody ScheduleAssignmentRequest request,
                                                     JwtAuthenticationToken auth) {
        return service.assignSchedule(org(auth), actor(auth), request);
    }

    @PutMapping("/subject-requirements") @PreAuthorize(MANAGE)
    public SubjectRequirementResponse saveRequirement(@Valid @RequestBody SubjectRequirementRequest request,
                                                      JwtAuthenticationToken auth) {
        return service.saveRequirement(org(auth), actor(auth), request);
    }

    @PutMapping("/teacher-policies") @PreAuthorize(MANAGE)
    public Map<String, Long> saveTeacherPolicy(@Valid @RequestBody TeacherPolicyRequest request,
                                              JwtAuthenticationToken auth) {
        return Map.of("id", service.saveTeacherPolicy(org(auth), actor(auth), request));
    }

    @PostMapping("/teacher-availability") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(MANAGE)
    public Map<String, Long> addTeacherAvailability(@Valid @RequestBody TeacherAvailabilityRequest request,
                                                   JwtAuthenticationToken auth) {
        return Map.of("id", service.addTeacherAvailability(org(auth), actor(auth), request));
    }

    @PutMapping("/teacher-availability/{id}") @PreAuthorize(MANAGE)
    public TeacherAvailabilityResponse updateTeacherAvailability(@PathVariable @Positive long id,
                                                                  @Valid @RequestBody TeacherAvailabilityUpdateRequest request,
                                                                  JwtAuthenticationToken auth) {
        return service.updateTeacherAvailability(org(auth), actor(auth), id, request);
    }

    @DeleteMapping("/teacher-availability/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize(MANAGE)
    public void deleteTeacherAvailability(@PathVariable @Positive long id,
                                          @Valid @RequestBody VersionRequest request,
                                          JwtAuthenticationToken auth) {
        service.deleteTeacherAvailability(org(auth), actor(auth), id, request.expectedVersion());
    }

    @PutMapping("/alternate-teacher-eligibilities") @PreAuthorize(MANAGE)
    public Map<String, Long> saveAlternateEligibility(@Valid @RequestBody AlternateEligibilityRequest request,
                                                     JwtAuthenticationToken auth) {
        return Map.of("id", service.saveAlternateEligibility(org(auth), actor(auth), request));
    }

    @GetMapping("/rooms") @PreAuthorize(VIEW)
    public List<RoomResponse> rooms(JwtAuthenticationToken auth) { return service.rooms(org(auth)); }

    @PostMapping("/rooms") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(MANAGE)
    public RoomResponse createRoom(@Valid @RequestBody RoomRequest request, JwtAuthenticationToken auth) {
        return service.createRoom(org(auth), actor(auth), request);
    }

    @PutMapping("/rooms/{id}") @PreAuthorize(MANAGE)
    public RoomResponse updateRoom(@PathVariable @Positive long id, @Valid @RequestBody RoomRequest request,
                                   JwtAuthenticationToken auth) {
        return service.updateRoom(org(auth), actor(auth), id, request);
    }

    @PutMapping("/section-home-rooms") @PreAuthorize(MANAGE)
    public SectionHomeRoomResponse assignSectionHomeRoom(@Valid @RequestBody SectionHomeRoomRequest request,
                                                         JwtAuthenticationToken auth) {
        return service.assignSectionHomeRoom(org(auth), actor(auth), request);
    }

    @PutMapping("/subject-room-requirements") @PreAuthorize(MANAGE)
    public Map<String, Long> saveSubjectRoomRequirement(@Valid @RequestBody SubjectRoomRequirementRequest request,
                                                       JwtAuthenticationToken auth) {
        return Map.of("id", service.saveSubjectRoomRequirement(org(auth), actor(auth), request));
    }

    @GetMapping("/academic-years/{academicYearId}/sections/{sectionId}/resolved-schedule") @PreAuthorize(VIEW)
    public ResolvedSectionSchedule resolvedSchedule(@PathVariable @Positive long academicYearId,
                                                    @PathVariable @Positive long sectionId,
                                                    JwtAuthenticationToken auth) {
        return service.resolvedSchedule(org(auth), academicYearId, sectionId);
    }

    @GetMapping("/academic-years/{academicYearId}") @PreAuthorize(VIEW)
    public AcademicYearConfiguration academicYearConfiguration(@PathVariable @Positive long academicYearId,
                                                               JwtAuthenticationToken auth) {
        return service.academicYearConfiguration(org(auth), academicYearId);
    }

    @GetMapping("/academic-years/{academicYearId}/options") @PreAuthorize(VIEW)
    public ConfigurationOptions configurationOptions(@PathVariable @Positive long academicYearId,
                                                     JwtAuthenticationToken auth) {
        return service.configurationOptions(org(auth), academicYearId);
    }

    @GetMapping("/academic-years/{academicYearId}/feasibility") @PreAuthorize(VIEW)
    public FeasibilityReport feasibility(@PathVariable @Positive long academicYearId, JwtAuthenticationToken auth) {
        return service.feasibility(org(auth), academicYearId);
    }

    private long org(JwtAuthenticationToken auth) { return AuthenticatedAcademicActor.organizationId(auth); }
    private long actor(JwtAuthenticationToken auth) { return AuthenticatedAcademicActor.userId(auth); }
}
