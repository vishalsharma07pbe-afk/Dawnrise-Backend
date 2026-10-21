package com.dawnrise.academic.timetable.controller;

import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.ConflictPreview;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.ConflictPreviewRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.CreateDraftRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.DraftResponse;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.DraftPage;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.DraftHistory;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.EffectiveDateIntentRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.WorkflowStatus;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.LessonRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.MoveLessonRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.SectionDraftResponse;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.SlotResponse;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.SwapLessonsRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.ValidationReport;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.VersionRequest;
import com.dawnrise.academic.timetable.service.TimetableDraftService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/timetable-drafts")
public class TimetableDraftController {
    private static final String VIEW="@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('TIMETABLE_DRAFT_VIEW')";
    private static final String EDIT="@academicTenantSecurity.hasOrganization(authentication) and hasAuthority('TIMETABLE_DRAFT_EDIT')";
    private final TimetableDraftService service;
    public TimetableDraftController(TimetableDraftService service){this.service=service;}

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(EDIT)
    public DraftResponse create(@Valid @RequestBody CreateDraftRequest request, JwtAuthenticationToken auth){return service.create(org(auth),actor(auth),request);}
    @GetMapping @PreAuthorize(VIEW)
    public DraftPage list(@RequestParam @Positive long academicYearId,
                          @RequestParam(required=false) WorkflowStatus status,
                          @RequestParam(defaultValue="0") @Min(0) int page,
                          @RequestParam(defaultValue="20") @Min(1) @Max(100) int size,
                          JwtAuthenticationToken auth) {
        return service.list(org(auth), academicYearId, status, page, size);
    }
    @GetMapping("/{draftId}") @PreAuthorize(VIEW)
    public DraftResponse get(@PathVariable @Positive long draftId,JwtAuthenticationToken auth){return service.get(org(auth),draftId);}
    @GetMapping("/{draftId}/history") @PreAuthorize(VIEW)
    public DraftHistory history(@PathVariable @Positive long draftId, JwtAuthenticationToken auth) {
        return service.history(org(auth), draftId);
    }
    @PutMapping("/{draftId}/effective-date-intent") @PreAuthorize(EDIT)
    public DraftResponse updateIntent(@PathVariable @Positive long draftId,
                                      @Valid @RequestBody EffectiveDateIntentRequest request,
                                      JwtAuthenticationToken auth) {
        return service.updateIntent(org(auth), actor(auth), draftId, request);
    }
    @GetMapping("/{draftId}/sections/{sectionId}") @PreAuthorize(VIEW)
    public SectionDraftResponse section(@PathVariable @Positive long draftId,@PathVariable @Positive long sectionId,JwtAuthenticationToken auth){return service.section(org(auth),draftId,sectionId);}
    @PostMapping("/{draftId}/conflict-preview") @PreAuthorize(EDIT)
    public ConflictPreview preview(@PathVariable @Positive long draftId,@Valid @RequestBody ConflictPreviewRequest request,JwtAuthenticationToken auth){return service.preview(org(auth),draftId,request.slotId(),request.proposed());}
    @PostMapping("/{draftId}/lessons") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(EDIT)
    public SlotResponse add(@PathVariable @Positive long draftId,@Valid @RequestBody LessonRequest request,JwtAuthenticationToken auth){return service.add(org(auth),actor(auth),draftId,request);}
    @PutMapping("/{draftId}/lessons/{slotId}/move") @PreAuthorize(EDIT)
    public SlotResponse move(@PathVariable @Positive long draftId,@PathVariable @Positive long slotId,@Valid @RequestBody MoveLessonRequest request,JwtAuthenticationToken auth){return service.move(org(auth),actor(auth),draftId,slotId,request);}
    @PostMapping("/{draftId}/lessons/{slotId}/swap") @PreAuthorize(EDIT)
    public DraftResponse swap(@PathVariable @Positive long draftId,@PathVariable @Positive long slotId,@Valid @RequestBody SwapLessonsRequest request,JwtAuthenticationToken auth){return service.swap(org(auth),actor(auth),draftId,slotId,request);}
    @PostMapping("/{draftId}/lessons/{slotId}/swap-preview") @PreAuthorize(EDIT)
    public ConflictPreview previewSwap(@PathVariable @Positive long draftId,@PathVariable @Positive long slotId,@Valid @RequestBody SwapLessonsRequest request,JwtAuthenticationToken auth){return service.previewSwap(org(auth),draftId,slotId,request);}
    @DeleteMapping("/{draftId}/lessons/{slotId}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize(EDIT)
    public void remove(@PathVariable @Positive long draftId,@PathVariable @Positive long slotId,@Valid @RequestBody VersionRequest request,JwtAuthenticationToken auth){service.remove(org(auth),actor(auth),draftId,slotId,request);}
    @PutMapping("/{draftId}/lessons/{slotId}/pin") @PreAuthorize(EDIT)
    public SlotResponse pin(@PathVariable @Positive long draftId,@PathVariable @Positive long slotId,@Valid @RequestBody VersionRequest request,JwtAuthenticationToken auth){return service.pin(org(auth),actor(auth),draftId,slotId,request,true);}
    @DeleteMapping("/{draftId}/lessons/{slotId}/pin") @PreAuthorize(EDIT)
    public SlotResponse unpin(@PathVariable @Positive long draftId,@PathVariable @Positive long slotId,@Valid @RequestBody VersionRequest request,JwtAuthenticationToken auth){return service.pin(org(auth),actor(auth),draftId,slotId,request,false);}
    @GetMapping("/{draftId}/validation") @PreAuthorize(VIEW)
    public ValidationReport validate(@PathVariable @Positive long draftId,JwtAuthenticationToken auth){return service.validate(org(auth),draftId);}
    private long org(JwtAuthenticationToken a){return AuthenticatedAcademicActor.organizationId(a);}private long actor(JwtAuthenticationToken a){return AuthenticatedAcademicActor.userId(a);}
}
