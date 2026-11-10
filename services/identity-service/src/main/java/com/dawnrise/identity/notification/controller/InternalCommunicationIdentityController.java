package com.dawnrise.identity.notification.controller;

import com.dawnrise.identity.notification.dto.CommunicationIdentityDtos;
import com.dawnrise.identity.notification.service.CommunicationIdentityDirectory;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/organizations/{organizationId}/communication-directory")
@PreAuthorize("hasRole('INTERNAL_SERVICE') and authentication.name == 'school-service'")
public class InternalCommunicationIdentityController {
    private final CommunicationIdentityDirectory directory;
    public InternalCommunicationIdentityController(CommunicationIdentityDirectory directory) { this.directory = directory; }

    @PostMapping("/users/search")
    public CommunicationIdentityDtos.Page search(@PathVariable long organizationId, @RequestBody CommunicationIdentityDtos.SearchRequest request) {
        return directory.search(organizationId, request);
    }

    @PostMapping("/groups")
    public CommunicationIdentityDtos.Groups groups(@PathVariable long organizationId) { return new CommunicationIdentityDtos.Groups(directory.groups(organizationId)); }

    @PostMapping("/users/resolve")
    public CommunicationIdentityDtos.Ids resolve(@PathVariable long organizationId, @Valid @RequestBody CommunicationIdentityDtos.ResolveUsersRequest request) {
        return new CommunicationIdentityDtos.Ids(directory.resolveActiveUsers(organizationId, request.userIds()));
    }

    @PostMapping("/guardians/resolve")
    public CommunicationIdentityDtos.Ids guardians(@PathVariable long organizationId, @Valid @RequestBody CommunicationIdentityDtos.ResolveGuardiansRequest request) {
        return new CommunicationIdentityDtos.Ids(directory.activeGuardians(organizationId, request.studentUserIds()));
    }

    @PostMapping("/users/{userId}/authority")
    public CommunicationIdentityDtos.Authority authority(@PathVariable long organizationId, @PathVariable long userId) {
        return directory.authority(organizationId, userId);
    }
}
