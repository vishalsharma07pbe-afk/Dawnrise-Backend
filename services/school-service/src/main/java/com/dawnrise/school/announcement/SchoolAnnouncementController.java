package com.dawnrise.school.announcement;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/announcements") @PreAuthorize("hasAuthority('SCHOOL_ANNOUNCEMENT_MANAGE') and @schoolTenantSecurity.hasOrganization(authentication)")
public class SchoolAnnouncementController {
 private final SchoolAnnouncementService service; public SchoolAnnouncementController(SchoolAnnouncementService service){this.service=service;}
 @PostMapping public AnnouncementDtos.View create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody AnnouncementDtos.Create request){return service.create(org(jwt),user(jwt),request);}
 @PostMapping("/preview") public AnnouncementDtos.Create preview(@Valid @RequestBody AnnouncementDtos.Create request){return service.preview(request);}
 @GetMapping("/{id}") public AnnouncementDtos.View get(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return service.get(org(jwt),id);}
 @GetMapping public List<AnnouncementDtos.View> list(@AuthenticationPrincipal Jwt jwt){return service.list(org(jwt));}
 @GetMapping("/{id}/audit") public List<AnnouncementDtos.Audit> audit(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return service.audit(org(jwt),id);}
 @PostMapping("/{id}/publish") public AnnouncementDtos.View publish(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody AnnouncementDtos.Version v){return service.publish(org(jwt),user(jwt),id,v.version());}
 @PostMapping("/{id}/cancel") public AnnouncementDtos.View cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody AnnouncementDtos.Version v){return service.cancel(org(jwt),user(jwt),id,v.version());}
 @PostMapping("/{id}/schedule") public AnnouncementDtos.View schedule(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody AnnouncementDtos.Schedule v){return service.schedule(org(jwt),user(jwt),id,v);}
 private long org(Jwt jwt){return ((Number)jwt.getClaim("organizationId")).longValue();} private long user(Jwt jwt){return Long.parseLong(jwt.getSubject());}
}
