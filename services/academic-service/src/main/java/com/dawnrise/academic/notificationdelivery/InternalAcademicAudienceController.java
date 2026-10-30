package com.dawnrise.academic.notificationdelivery;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Set;
@RestController @RequestMapping("/internal/v1/organizations/{organizationId}/notification-audience")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalAcademicAudienceController {
    private final AcademicAudienceResolver resolver;
    public InternalAcademicAudienceController(AcademicAudienceResolver resolver){this.resolver=resolver;}
    @GetMapping("/{type}/{id}") public Response resolve(@PathVariable long organizationId,@PathVariable String type,@PathVariable long id){return new Response(resolver.resolve(organizationId,type,id));}
    public record Response(Set<Long> studentUserIds){}
}
