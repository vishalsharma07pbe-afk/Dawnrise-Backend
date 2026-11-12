package com.dawnrise.school.communication;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/v1/communications")
@PreAuthorize("@schoolTenantSecurity.hasOrganization(authentication) and (hasAuthority('COMMUNICATION_NOTICE_SEND_SCOPED') or hasAuthority('COMMUNICATION_NOTICE_SEND_SCHOOL_WIDE'))")
public class CommunicationAudienceController {
    private final CommunicationAudienceService service;
    private final CommunicationNoticeService notices;
    public CommunicationAudienceController(CommunicationAudienceService service,CommunicationNoticeService notices){this.service=service;this.notices=notices;}
    @GetMapping("/recipients") public CommunicationDtos.Page recipients(Authentication authentication,@RequestParam(defaultValue="") String query,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.recipients(org(authentication),actor(authentication),schoolWide(authentication),query,page,size);}
    @GetMapping("/audiences") public CommunicationDtos.Page audiences(Authentication authentication,@RequestParam(defaultValue="") String query,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.groups(org(authentication),actor(authentication),schoolWide(authentication),query,page,size);}
    @PostMapping("/audiences/preview") public CommunicationDtos.Preview preview(Authentication authentication,@Valid @RequestBody CommunicationDtos.PreviewRequest request){return service.preview(org(authentication),actor(authentication),schoolWide(authentication),request);}
    @PostMapping("/notices") public CommunicationDtos.Notice create(Authentication authentication,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CommunicationDtos.CreateNotice request){return notices.create(org(authentication),actor(authentication),schoolWide(authentication),key,request);}
    @GetMapping("/notices") public CommunicationDtos.NoticePage history(Authentication authentication,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return notices.list(org(authentication),actor(authentication),page,size);}
    @GetMapping("/notices/{id}") public CommunicationDtos.Notice detail(Authentication authentication,@PathVariable long id){return notices.get(org(authentication),actor(authentication),id);}
    @PostMapping("/notices/{id}/publish") public CommunicationDtos.Notice publish(Authentication authentication,@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CommunicationDtos.VersionMutation request){return notices.publish(org(authentication),actor(authentication),schoolWide(authentication),id,key,request.version());}
    @PostMapping("/notices/{id}/schedule") public CommunicationDtos.Notice schedule(Authentication authentication,@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CommunicationDtos.ScheduleMutation request){return notices.schedule(org(authentication),actor(authentication),schoolWide(authentication),id,key,request);}
    @PostMapping("/notices/{id}/cancel") public CommunicationDtos.Notice cancel(Authentication authentication,@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CommunicationDtos.VersionMutation request){return notices.cancel(org(authentication),actor(authentication),id,key,request.version());}
    private static Jwt jwt(Authentication authentication){return ((JwtAuthenticationToken)authentication).getToken();}
    private static long org(Authentication authentication){return ((Number)jwt(authentication).getClaim("organizationId")).longValue();}
    private static long actor(Authentication authentication){return Long.parseLong(jwt(authentication).getSubject());}
    private static boolean schoolWide(Authentication authentication){return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("COMMUNICATION_NOTICE_SEND_SCHOOL_WIDE"));}
}
