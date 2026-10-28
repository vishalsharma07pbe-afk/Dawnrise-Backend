package com.dawnrise.academic.timetable.controller;

import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import com.dawnrise.academic.timetable.dto.TimetableWorkflowDtos.PublishRequest;
import com.dawnrise.academic.timetable.dto.TimetableWorkflowDtos.PublishedTimetable;
import com.dawnrise.academic.timetable.dto.TimetableWorkflowDtos.ReturnRequest;
import com.dawnrise.academic.timetable.dto.TimetableWorkflowDtos.SchoolCalendarDate;
import com.dawnrise.academic.timetable.dto.TimetableWorkflowDtos.TransitionRequest;
import com.dawnrise.academic.timetable.dto.TimetableWorkflowDtos.WorkflowResponse;
import com.dawnrise.academic.timetable.service.TimetablePdfService;
import com.dawnrise.academic.timetable.service.TimetableWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/v1/timetables")
public class TimetableWorkflowController {
    private static final String TENANT="@academicTenantSecurity.hasOrganization(authentication)";
    private static final String STAFF_VIEW=TENANT+" and hasAuthority('TIMETABLE_PUBLISHED_VIEW')";
    private final TimetableWorkflowService service;private final TimetablePdfService pdf;
    public TimetableWorkflowController(TimetableWorkflowService service,TimetablePdfService pdf){this.service=service;this.pdf=pdf;}

    @PostMapping("/drafts/{draftId}/submit") @PreAuthorize(TENANT+" and hasAuthority('TIMETABLE_SUBMIT')")
    public WorkflowResponse submit(@PathVariable @Positive long draftId,@Valid @RequestBody TransitionRequest request,JwtAuthenticationToken auth){return service.submit(org(auth),actor(auth),draftId,request.expectedVersion());}
    @PostMapping("/drafts/{draftId}/return") @PreAuthorize(TENANT+" and hasAuthority('TIMETABLE_REVIEW')")
    public WorkflowResponse returnDraft(@PathVariable @Positive long draftId,@Valid @RequestBody ReturnRequest request,JwtAuthenticationToken auth){return service.returnDraft(org(auth),actor(auth),draftId,request.expectedVersion(),request.reason());}
    @PostMapping("/drafts/{draftId}/approve") @PreAuthorize(TENANT+" and hasAuthority('TIMETABLE_APPROVE')")
    public WorkflowResponse approve(@PathVariable @Positive long draftId,@Valid @RequestBody TransitionRequest request,JwtAuthenticationToken auth){return service.approve(org(auth),actor(auth),draftId,request.expectedVersion());}
    @PostMapping("/drafts/{draftId}/publish") @PreAuthorize(TENANT+" and hasAuthority('TIMETABLE_PUBLISH')")
    public WorkflowResponse publish(@PathVariable @Positive long draftId,@Valid @RequestBody PublishRequest request,JwtAuthenticationToken auth){return service.publish(org(auth),actor(auth),draftId,request.expectedVersion(),request.effectiveFrom(),request.effectiveToExclusive());}

    @GetMapping("/published/current-date")
    @PreAuthorize(TENANT+" and (hasAuthority('TIMETABLE_PUBLISHED_VIEW') or hasAuthority('TIMETABLE_PUBLISHED_VIEW_SELF'))")
    public SchoolCalendarDate currentDate(JwtAuthenticationToken auth){return service.currentSchoolDate(org(auth));}

    @GetMapping("/published/academic-years/{yearId}/school") @PreAuthorize(STAFF_VIEW)
    public PublishedTimetable school(@PathVariable @Positive long yearId,@RequestParam(required=false) LocalDate date,JwtAuthenticationToken auth){return service.school(org(auth),yearId,date);}
    @GetMapping("/published/academic-years/{yearId}/wings/{wingId}") @PreAuthorize(STAFF_VIEW)
    public PublishedTimetable wing(@PathVariable @Positive long yearId,@PathVariable @Positive long wingId,@RequestParam(required=false) LocalDate date,JwtAuthenticationToken auth){return service.wing(org(auth),yearId,wingId,date);}
    @GetMapping("/published/academic-years/{yearId}/grades/{gradeId}") @PreAuthorize(STAFF_VIEW)
    public PublishedTimetable grade(@PathVariable @Positive long yearId,@PathVariable @Positive long gradeId,@RequestParam(required=false) LocalDate date,JwtAuthenticationToken auth){return service.grade(org(auth),yearId,gradeId,date);}
    @GetMapping("/published/academic-years/{yearId}/sections/{sectionId}") @PreAuthorize(STAFF_VIEW)
    public PublishedTimetable section(@PathVariable @Positive long yearId,@PathVariable @Positive long sectionId,@RequestParam(required=false) LocalDate date,JwtAuthenticationToken auth){return service.section(org(auth),yearId,sectionId,date);}
    @GetMapping("/published/academic-years/{yearId}/teachers/{teacherId}") @PreAuthorize(STAFF_VIEW)
    public PublishedTimetable teacher(@PathVariable @Positive long yearId,@PathVariable @Positive long teacherId,@RequestParam(required=false) LocalDate date,JwtAuthenticationToken auth){return service.teacher(org(auth),yearId,teacherId,date);}
    @GetMapping("/published/academic-years/{yearId}/teacher/self") @PreAuthorize(TENANT+" and hasRole('TEACHER') and hasAuthority('TIMETABLE_PUBLISHED_VIEW_SELF')")
    public PublishedTimetable teacherSelf(@PathVariable @Positive long yearId,@RequestParam(required=false) LocalDate date,JwtAuthenticationToken auth){return service.teacher(org(auth),yearId,actor(auth),date);}
    @GetMapping("/published/academic-years/{yearId}/student/self") @PreAuthorize(TENANT+" and hasRole('STUDENT') and hasAuthority('TIMETABLE_PUBLISHED_VIEW_SELF')")
    public PublishedTimetable studentSelf(@PathVariable @Positive long yearId,@RequestParam(required=false) LocalDate date,JwtAuthenticationToken auth){return service.student(org(auth),actor(auth),yearId,date);}
    @GetMapping("/published/academic-years/{yearId}/parent/students/{studentId}") @PreAuthorize(TENANT+" and hasRole('PARENT') and hasAuthority('TIMETABLE_PUBLISHED_VIEW_SELF')")
    public PublishedTimetable parent(@PathVariable @Positive long yearId,@PathVariable @Positive long studentId,@RequestParam(required=false) LocalDate date,JwtAuthenticationToken auth){return service.parent(org(auth),actor(auth),studentId,yearId,date);}

    @GetMapping("/published/academic-years/{yearId}/school.pdf") @PreAuthorize(STAFF_VIEW)
    public ResponseEntity<byte[]> schoolPdf(@PathVariable @Positive long yearId,@RequestParam(required=false) LocalDate date,JwtAuthenticationToken auth){return pdf(service.school(org(auth),yearId,date),"FULL SCHOOL TIMETABLE");}
    @GetMapping("/published/academic-years/{yearId}/wings/{wingId}.pdf") @PreAuthorize(STAFF_VIEW)
    public ResponseEntity<byte[]> wingPdf(@PathVariable @Positive long yearId,@PathVariable @Positive long wingId,@RequestParam(required=false) LocalDate date,JwtAuthenticationToken auth){return pdf(service.wing(org(auth),yearId,wingId,date),"WING TIMETABLE");}
    @GetMapping("/published/academic-years/{yearId}/grades/{gradeId}.pdf") @PreAuthorize(STAFF_VIEW)
    public ResponseEntity<byte[]> gradePdf(@PathVariable @Positive long yearId,@PathVariable @Positive long gradeId,@RequestParam(required=false) LocalDate date,JwtAuthenticationToken auth){return pdf(service.grade(org(auth),yearId,gradeId,date),"GRADE TIMETABLE");}
    @GetMapping("/published/academic-years/{yearId}/sections/{sectionId}.pdf") @PreAuthorize(STAFF_VIEW)
    public ResponseEntity<byte[]> sectionPdf(@PathVariable @Positive long yearId,@PathVariable @Positive long sectionId,@RequestParam(required=false) LocalDate date,JwtAuthenticationToken auth){return pdf(service.section(org(auth),yearId,sectionId,date),"SECTION TIMETABLE");}
    @GetMapping("/published/academic-years/{yearId}/teachers/{teacherId}.pdf") @PreAuthorize(STAFF_VIEW)
    public ResponseEntity<byte[]> teacherPdf(@PathVariable @Positive long yearId,@PathVariable @Positive long teacherId,@RequestParam(required=false) LocalDate date,JwtAuthenticationToken auth){return pdf(service.teacher(org(auth),yearId,teacherId,date),"TEACHER TIMETABLE");}
    @GetMapping("/published/academic-years/{yearId}/teacher/self.pdf") @PreAuthorize(TENANT+" and hasRole('TEACHER') and hasAuthority('TIMETABLE_PUBLISHED_VIEW_SELF')")
    public ResponseEntity<byte[]> teacherSelfPdf(@PathVariable @Positive long yearId,@RequestParam(required=false) LocalDate date,JwtAuthenticationToken auth){return pdf(service.teacher(org(auth),yearId,actor(auth),date),"TEACHER TIMETABLE");}
    @GetMapping("/published/academic-years/{yearId}/student/self.pdf") @PreAuthorize(TENANT+" and hasRole('STUDENT') and hasAuthority('TIMETABLE_PUBLISHED_VIEW_SELF')")
    public ResponseEntity<byte[]> studentSelfPdf(@PathVariable @Positive long yearId,@RequestParam(required=false) LocalDate date,JwtAuthenticationToken auth){return pdf(service.student(org(auth),actor(auth),yearId,date),"STUDENT TIMETABLE");}
    @GetMapping("/published/academic-years/{yearId}/parent/students/{studentId}.pdf") @PreAuthorize(TENANT+" and hasRole('PARENT') and hasAuthority('TIMETABLE_PUBLISHED_VIEW_SELF')")
    public ResponseEntity<byte[]> parentPdf(@PathVariable @Positive long yearId,@PathVariable @Positive long studentId,@RequestParam(required=false) LocalDate date,JwtAuthenticationToken auth){return pdf(service.parent(org(auth),actor(auth),studentId,yearId,date),"STUDENT TIMETABLE");}
    private ResponseEntity<byte[]> pdf(PublishedTimetable timetable,String title){return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"timetable-"+timetable.publicationId()+".pdf\"").body(pdf.published(timetable,title));}
    private long org(JwtAuthenticationToken auth){return AuthenticatedAcademicActor.organizationId(auth);}private long actor(JwtAuthenticationToken auth){return AuthenticatedAcademicActor.userId(auth);}
}
