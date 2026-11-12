package com.dawnrise.school.communication;

import com.dawnrise.school.announcement.NotificationRelayProperties;
import com.dawnrise.school.school.provisioning.IdentityProvisioningProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Set;

@Component
public class CommunicationDirectoryGateway {
    private final RestClient identity;
    private final RestClient academic;
    private final IdentityProvisioningProperties identityProperties;
    private final NotificationRelayProperties academicProperties;
    public CommunicationDirectoryGateway(@Qualifier("identityProvisioningRestClient") RestClient identity,
                                         @Qualifier("academicNotificationRestClient") RestClient academic,
                                         IdentityProvisioningProperties identityProperties,
                                         NotificationRelayProperties academicProperties) {
        this.identity = identity; this.academic = academic; this.identityProperties = identityProperties; this.academicProperties = academicProperties;
    }

    public IdentityPage searchUsers(long org, String query, int page, int size, Set<Long> allowed) {
        return searchUsers(org, query, page, size, allowed, Set.of());
    }
    public IdentityPage searchUsers(long org, String query, int page, int size, Set<Long> allowed, Set<Long> excluded) {
        return identity.post().uri("/internal/v1/organizations/{org}/communication-directory/users/search", org).headers(this::identityHeaders)
                .body(new SearchRequest(query, page, size, allowed, excluded)).retrieve().body(IdentityPage.class);
    }
    public List<IdentityGroup> identityGroups(long org) {
        IdentityGroups response = identity.post().uri("/internal/v1/organizations/{org}/communication-directory/groups", org).headers(this::identityHeaders).retrieve().body(IdentityGroups.class);
        return response == null ? List.of() : response.items();
    }
    public Set<Long> resolveUsers(long org, Set<Long> ids) { Ids response = identity.post().uri("/internal/v1/organizations/{org}/communication-directory/users/resolve", org).headers(this::identityHeaders).body(new UserIds(ids)).retrieve().body(Ids.class); return response == null ? Set.of() : response.userIds(); }
    public Set<Long> guardians(long org, Set<Long> students) { Ids response = identity.post().uri("/internal/v1/organizations/{org}/communication-directory/guardians/resolve", org).headers(this::identityHeaders).body(new StudentIds(students)).retrieve().body(Ids.class); return response == null ? Set.of() : response.userIds(); }
    public Set<Long> identityAudience(long org, String type, String key) { IdentityAudience response = identity.post().uri("/internal/v1/organizations/{org}/notification-audience/resolve", org).headers(this::identityHeaders).body(new IdentityAudienceRequest(List.of(new IdentityAudienceItem(type, key)))).retrieve().body(IdentityAudience.class); return response == null ? Set.of() : response.userIds(); }
    public List<AcademicGroup> academicGroups(long org, long actor, boolean schoolWide) { AcademicGroups response = academic.post().uri("/internal/v1/organizations/{org}/communication-audiences/groups", org).headers(this::academicHeaders).body(new Scope(actor, schoolWide)).retrieve().body(AcademicGroups.class); return response == null ? List.of() : response.items(); }
    public Set<Long> scopeStudents(long org, long actor, boolean schoolWide) { StudentIds response = academic.post().uri("/internal/v1/organizations/{org}/communication-audiences/scope-students", org).headers(this::academicHeaders).body(new Scope(actor, schoolWide)).retrieve().body(StudentIds.class); return response == null ? Set.of() : response.studentUserIds(); }
    public Set<Long> academicAudience(long org, long actor, boolean schoolWide, String type, long id) { StudentIds response = academic.post().uri("/internal/v1/organizations/{org}/communication-audiences/resolve", org).headers(this::academicHeaders).body(new AcademicResolve(actor, schoolWide, type, id)).retrieve().body(StudentIds.class); return response == null ? Set.of() : response.studentUserIds(); }
    public Authority senderAuthority(long org,long actor) { Authority response=identity.post().uri("/internal/v1/organizations/{org}/communication-directory/users/{actor}/authority",org,actor).headers(this::identityHeaders).retrieve().body(Authority.class);return response==null?new Authority(false,Set.of()):response; }
    public List<ChannelEligibility> eligibility(long org,Set<Long> users,Set<String> channels){Eligibility response=identity.post().uri("/internal/v1/organizations/{org}/communication-directory/users/eligibility",org).headers(this::identityHeaders).body(new EligibilityRequest(users,channels)).retrieve().body(Eligibility.class);return response==null?List.of():response.channels();}
    public List<ParentContact> parentContacts(long org,long student){ParentContacts response=academic.post().uri("/internal/v1/organizations/{org}/communication-audiences/parent-contacts/{student}",org,student).headers(this::academicHeaders).retrieve().body(ParentContacts.class);return response==null?List.of():response.items();}
    private void identityHeaders(org.springframework.http.HttpHeaders h) { h.set("X-Internal-Api-Key", identityProperties.getApiKey()); h.set("X-Service-Name", "school-service"); }
    private void academicHeaders(org.springframework.http.HttpHeaders h) { h.set("X-Internal-Api-Key", academicProperties.getApiKey()); h.set("X-Service-Name", "school-service"); }

    record SearchRequest(String query,int page,int size,Set<Long> allowedUserIds,Set<Long> excludedUserIds){} record UserIds(Set<Long> userIds){} record StudentIds(Set<Long> studentUserIds){} record Ids(Set<Long> userIds){}
    public record IdentityUser(long id,String displayName,List<String> roles){} public record IdentityPage(List<IdentityUser> items,int page,int size,long total){}
    public record IdentityGroup(String type,String key,String label,String detail){} record IdentityGroups(List<IdentityGroup> items){}
    record IdentityAudienceItem(String type,String key){} record IdentityAudienceRequest(List<IdentityAudienceItem> audiences){} record IdentityAudience(Set<Long> userIds){}
    record Scope(long actorUserId,boolean schoolWide){} record AcademicResolve(long actorUserId,boolean schoolWide,String type,long id){}
    public record AcademicGroup(String type,long id,String label,String detail){} record AcademicGroups(List<AcademicGroup> items){}
    public record Authority(boolean active,Set<String> permissions){}
    public record ParentContact(long userId,String kind){} record ParentContacts(List<ParentContact> items){}
    record EligibilityRequest(Set<Long> userIds,Set<String> channels){} record Eligibility(List<ChannelEligibility> channels){} public record ChannelEligibility(String channel,int eligibleCount,int excludedCount,boolean available,String reason){}
}
