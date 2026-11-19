package com.dawnrise.academic.learning;

import com.dawnrise.academic.learning.LearningDtos.*;
import com.dawnrise.academic.security.AuthenticatedAcademicActor;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/v1/classwork-homework")
@PreAuthorize("hasAnyRole('TEACHER','STUDENT','PARENT','GUARDIAN','ADMIN','PRINCIPAL','VICE_PRINCIPAL','HEAD_TEACHER')")
public class LearningController {
    private final LearningService service;
    public LearningController(LearningService service){this.service=service;}

    @GetMapping("/scopes") @PreAuthorize("hasRole('TEACHER')")
    public List<Scope> scopes(JwtAuthenticationToken a){return service.scopes(org(a),user(a));}
    @GetMapping("/teacher-overview") @PreAuthorize("hasRole('TEACHER')")
    public List<TeacherHomeworkSummary> teacherOverview(JwtAuthenticationToken a){return service.teacherHomework(org(a),user(a));}
    @GetMapping public List<Post> list(JwtAuthenticationToken a,@RequestParam(required=false) Long studentUserId){return service.list(org(a),user(a),roles(a),studentUserId);}
    @GetMapping("/{id}") public Post get(JwtAuthenticationToken a,@PathVariable long id,@RequestParam(required=false) Long studentUserId){service.requirePostAccess(org(a),user(a),id,roles(a),studentUserId);return service.get(org(a),id,studentUserId);}
    @PostMapping @PreAuthorize("hasRole('TEACHER')") public ResponseEntity<Post> create(JwtAuthenticationToken a,@Valid @RequestBody Save body){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(org(a),user(a),body));}
    @PutMapping("/{id}") @PreAuthorize("hasRole('TEACHER')") public Post update(JwtAuthenticationToken a,@PathVariable long id,@RequestParam long version,@Valid @RequestBody Save body){return service.update(org(a),user(a),id,version,body);}
    @PostMapping("/{id}/publish") @PreAuthorize("hasRole('TEACHER')") public Post publish(JwtAuthenticationToken a,@PathVariable long id,@RequestParam long version){return service.publish(org(a),user(a),id,version);}
    @PostMapping("/{id}/withdraw") @PreAuthorize("hasRole('TEACHER')") public Post withdraw(JwtAuthenticationToken a,@PathVariable long id,@RequestParam long version){return service.withdraw(org(a),user(a),id,version);}
    @PostMapping(value="/{id}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasRole('TEACHER')") public Attachment upload(JwtAuthenticationToken a,@PathVariable long id,@RequestParam(required=false) UUID taskId,@RequestPart("file") MultipartFile file){return service.upload(org(a),user(a),id,taskId,file);}
    @DeleteMapping("/{post}/attachments/{id}") @PreAuthorize("hasRole('TEACHER')") public ResponseEntity<Void> removeAttachment(JwtAuthenticationToken a,@PathVariable long post,@PathVariable UUID id){service.removeAttachment(org(a),user(a),post,id);return ResponseEntity.noContent().build();}
    @GetMapping("/{post}/attachments/{id}") public ResponseEntity<byte[]> download(JwtAuthenticationToken a,@PathVariable long post,@PathVariable UUID id,@RequestParam(required=false) Long studentUserId){service.requirePostAccess(org(a),user(a),post,roles(a),studentUserId);var value=service.download(org(a),post,id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(value.contentType())).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(value.name()).build().toString()).header("X-Content-Type-Options","nosniff").body(value.content());}
    @PutMapping("/{post}/tasks/{task}") @PreAuthorize("hasRole('STUDENT')") public Review toggle(JwtAuthenticationToken a,@PathVariable long post,@PathVariable UUID task,@RequestParam boolean complete){return service.toggle(org(a),user(a),post,task,complete);}
    @PostMapping("/{post}/ready") @PreAuthorize("hasRole('STUDENT')") public Review ready(JwtAuthenticationToken a,@PathVariable long post){return service.ready(org(a),user(a),post);}
    @PostMapping("/{post}/reminders") @PreAuthorize("hasRole('TEACHER')") public ReminderResult remind(JwtAuthenticationToken a,@PathVariable long post,@Valid @RequestBody ReminderRequest body){return service.remind(org(a),user(a),post,body);}
    @GetMapping("/review-queue") @PreAuthorize("hasRole('TEACHER')") public List<ReviewQueueItem> reviewQueue(JwtAuthenticationToken a){return service.reviewQueue(org(a),user(a));}
    @PostMapping("/{post}/students/{student}/review") @PreAuthorize("hasRole('TEACHER')") public Review review(JwtAuthenticationToken a,@PathVariable long post,@PathVariable long student,@Valid @RequestBody ReviewDecision d){return service.decide(org(a),user(a),post,student,d);}
    @GetMapping("/students/{student}/points") public PointsSummary points(JwtAuthenticationToken a,@PathVariable long student){service.requirePointsAccess(org(a),user(a),student,roles(a));return service.points(org(a),student);}
    @GetMapping("/settings/{academicYearId}") public RewardSettings settings(JwtAuthenticationToken a,@PathVariable long academicYearId){return service.settings(org(a),academicYearId);}
    @PutMapping("/settings") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','VICE_PRINCIPAL','HEAD_TEACHER')") public RewardSettings settings(JwtAuthenticationToken a,@Valid @RequestBody SaveRewardSettings input){return service.saveSettings(org(a),user(a),input);}
    @PostMapping("/points/corrections") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','VICE_PRINCIPAL')") public PointEntry correct(JwtAuthenticationToken a,@Valid @RequestBody PointCorrection input){return service.correctPoints(org(a),user(a),input);}
    @GetMapping("/leadership-progress") @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','VICE_PRINCIPAL','HEAD_TEACHER')") public List<LeadershipProgress> leadershipProgress(JwtAuthenticationToken a){return service.leadershipProgress(org(a));}
    private long org(JwtAuthenticationToken a){return AuthenticatedAcademicActor.organizationId(a);}
    private long user(JwtAuthenticationToken a){return AuthenticatedAcademicActor.userId(a);}
    private String roles(JwtAuthenticationToken a){return a.getAuthorities().stream().map(Object::toString).reduce("",(x,y)->x+" "+y);}
}
