package com.dawnrise.academic.notificationdelivery;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/organizations/{organizationId}/communication-audiences")
@PreAuthorize("hasRole('INTERNAL_SERVICE') and authentication.name == 'school-service'")
public class InternalCommunicationAcademicController {
    private final CommunicationAcademicDirectory directory;
    public InternalCommunicationAcademicController(CommunicationAcademicDirectory directory) { this.directory = directory; }
    @PostMapping("/groups") public CommunicationAcademicDtos.Groups groups(@PathVariable long organizationId, @RequestBody CommunicationAcademicDtos.ScopeRequest request) { return new CommunicationAcademicDtos.Groups(directory.groups(organizationId, request.actorUserId(), request.schoolWide())); }
    @PostMapping("/scope-students") public CommunicationAcademicDtos.Ids scope(@PathVariable long organizationId, @RequestBody CommunicationAcademicDtos.ScopeRequest request) { return new CommunicationAcademicDtos.Ids(directory.scopeStudents(organizationId, request.actorUserId(), request.schoolWide())); }
    @PostMapping("/resolve") public CommunicationAcademicDtos.Ids resolve(@PathVariable long organizationId, @RequestBody CommunicationAcademicDtos.ResolveRequest request) { return new CommunicationAcademicDtos.Ids(directory.resolve(organizationId, request)); }
    @PostMapping("/parent-contacts/{studentUserId}") public CommunicationAcademicDtos.ParentContacts parentContacts(@PathVariable long organizationId,@PathVariable long studentUserId){return new CommunicationAcademicDtos.ParentContacts(directory.parentContacts(organizationId,studentUserId));}
}
