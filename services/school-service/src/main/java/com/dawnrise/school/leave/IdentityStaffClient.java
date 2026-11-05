package com.dawnrise.school.leave;

import com.dawnrise.school.school.provisioning.IdentityProvisioningProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Set;

@Component
public class IdentityStaffClient {
    private final RestClient client;
    private final IdentityProvisioningProperties properties;

    public IdentityStaffClient(@Qualifier("identityProvisioningRestClient") RestClient client,
                               IdentityProvisioningProperties properties) {
        this.client=client; this.properties=properties;
    }

    public Staff staff(long organizationId,long userId) {
        Staff value=client.get().uri("/internal/v1/organizations/{organizationId}/staff-directory/{userId}",organizationId,userId)
                .headers(this::headers).retrieve().body(Staff.class);
        if(value==null||!"ACTIVE".equals(value.status())||value.roles()==null||value.roles().isEmpty())
            throw new IllegalArgumentException("Staff member is not active in this organization");
        return value;
    }

    public List<Staff> approvers(long organizationId,List<String> roles) {
        Staff[] values=client.get().uri(builder->builder.path("/internal/v1/organizations/{organizationId}/staff-directory/approvers")
                .queryParam("roles",roles).build(organizationId)).headers(this::headers).retrieve().body(Staff[].class);
        return values==null?List.of():List.of(values);
    }

    public List<String> activeStaffRoles(long organizationId) {
        String[] values=client.get().uri("/internal/v1/organizations/{organizationId}/staff-directory/roles",organizationId)
                .headers(this::headers).retrieve().body(String[].class);
        return values==null?List.of():List.of(values);
    }

    private void headers(org.springframework.http.HttpHeaders headers) {
        headers.set("X-Internal-Api-Key",properties.getApiKey());
        headers.set("X-Service-Name",properties.getServiceName());
    }
    public record Staff(long userId,String status,String displayName,Set<String> roles) {}
}
