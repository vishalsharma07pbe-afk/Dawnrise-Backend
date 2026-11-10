package com.dawnrise.school.communication;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/parent-communications")
public class ParentConversationController {
    private final ParentConversationService service;
    public ParentConversationController(ParentConversationService service){this.service=service;}
    @GetMapping("/guardian/students/{student}/contacts") @PreAuthorize("hasAuthority('PARENT_COMMUNICATION_USE')") public List<ParentConversationDtos.Contact> contacts(JwtAuthenticationToken auth,@PathVariable long student,@RequestParam(defaultValue="")String query){return service.contacts(org(auth),user(auth),student,query);}
    @PostMapping("/guardian/threads") @PreAuthorize("hasAuthority('PARENT_COMMUNICATION_USE')") public ParentConversationDtos.Thread create(JwtAuthenticationToken auth,@RequestHeader("Idempotency-Key")String key,@Valid@RequestBody ParentConversationDtos.Create input){return service.create(org(auth),user(auth),key,input);}
    @PostMapping("/staff/threads") @PreAuthorize("hasAnyAuthority('COMMUNICATION_NOTICE_SEND_SCOPED','COMMUNICATION_NOTICE_SEND_SCHOOL_WIDE')") public ParentConversationDtos.Thread createDirect(JwtAuthenticationToken auth,@RequestHeader("Idempotency-Key")String key,@Valid@RequestBody ParentConversationDtos.CreateDirect input){return service.createDirect(org(auth),user(auth),schoolWide(auth),key,input);}
    @GetMapping("/threads") @PreAuthorize("@schoolTenantSecurity.hasOrganization(authentication)") public List<ParentConversationDtos.Thread> list(JwtAuthenticationToken auth){return service.list(org(auth),user(auth));}
    @GetMapping("/threads/{id}") @PreAuthorize("@schoolTenantSecurity.hasOrganization(authentication)") public ParentConversationDtos.Thread get(JwtAuthenticationToken auth,@PathVariable long id){return service.get(org(auth),user(auth),id);}
    @PostMapping("/threads/{id}/messages") @PreAuthorize("@schoolTenantSecurity.hasOrganization(authentication)") public ParentConversationDtos.Thread reply(JwtAuthenticationToken auth,@PathVariable long id,@RequestHeader("Idempotency-Key")String key,@Valid@RequestBody ParentConversationDtos.Reply input){return service.reply(org(auth),user(auth),id,key,input.body());}
    @PostMapping("/threads/{id}/read") @PreAuthorize("@schoolTenantSecurity.hasOrganization(authentication)") public ParentConversationDtos.Thread read(JwtAuthenticationToken auth,@PathVariable long id){return service.markRead(org(auth),user(auth),id);}
    @PostMapping("/guardian/threads/{id}/escalate-head") @PreAuthorize("hasAuthority('PARENT_COMMUNICATION_USE')") public ParentConversationDtos.Thread escalate(JwtAuthenticationToken auth,@PathVariable long id){return service.requestHead(org(auth),user(auth),id);}
    @PostMapping("/threads/{id}/resolve") @PreAuthorize("hasAnyAuthority('PARENT_COMMUNICATION_USE','PARENT_COMMUNICATION_RESPOND')") public ParentConversationDtos.Thread resolve(JwtAuthenticationToken auth,@PathVariable long id){return service.resolve(org(auth),user(auth),id);}
    @PostMapping("/threads/{id}/escalate-principal") @PreAuthorize("hasAuthority('PARENT_COMMUNICATION_RESPOND')") public ParentConversationDtos.Thread principal(JwtAuthenticationToken auth,@PathVariable long id){return service.escalatePrincipal(org(auth),user(auth),id);}
    @PutMapping("/admin/contacts") @PreAuthorize("hasAuthority('PARENT_COMMUNICATION_MANAGE_CONTACTS')") public void contact(JwtAuthenticationToken auth,@Valid@RequestBody ParentConversationDtos.ContactInput input){service.configureContact(org(auth),user(auth),input);}
    @PutMapping("/admin/policy") @PreAuthorize("hasAuthority('PARENT_COMMUNICATION_MANAGE_CONTACTS')") public void policy(JwtAuthenticationToken auth,@Valid@RequestBody ParentConversationDtos.PolicyInput input){service.configurePolicy(org(auth),user(auth),input);}
    private long org(JwtAuthenticationToken auth){return ((Number)auth.getToken().getClaim("organizationId")).longValue();}
    private long user(JwtAuthenticationToken auth){return Long.parseLong(auth.getToken().getSubject());}
    private boolean schoolWide(JwtAuthenticationToken auth){return auth.getAuthorities().stream().anyMatch(value->value.getAuthority().equals("COMMUNICATION_NOTICE_SEND_SCHOOL_WIDE"));}
}
