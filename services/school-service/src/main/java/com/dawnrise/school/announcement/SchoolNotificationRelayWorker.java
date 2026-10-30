package com.dawnrise.school.announcement;

import com.dawnrise.school.school.provisioning.IdentityProvisioningProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;import java.util.*;

@Component
public class SchoolNotificationRelayWorker {
 private final SchoolNotificationOutboxStore store;private final RestClient identity;private final RestClient academic;private final IdentityProvisioningProperties identityProperties;private final NotificationRelayProperties properties;private final ObjectMapper json;
 public SchoolNotificationRelayWorker(SchoolNotificationOutboxStore store,@Qualifier("identityProvisioningRestClient") RestClient identity,@Qualifier("academicNotificationRestClient") RestClient academic,IdentityProvisioningProperties identityProperties,NotificationRelayProperties properties,ObjectMapper json){this.store=store;this.identity=identity;this.academic=academic;this.identityProperties=identityProperties;this.properties=properties;this.json=json;}
 @Scheduled(fixedDelayString="${dawnrise.notification-relay.delay:PT10S}") public void deliver(){UUID owner=UUID.randomUUID();store.claim(owner,properties.getLease()).ifPresent(row->{try{Set<Long> recipients=resolve(row);if(recipients.isEmpty())throw new IllegalStateException("Audience resolved to no active recipients");identity.post().uri("/internal/v1/notification-events").headers(h->headers(h,identityProperties.getApiKey())).body(new Ingest(row.organizationId(),row.sourceEventId(),row.eventType(),"SCHOOL",row.importance(),row.heading(),row.context(),new Action(row.eventType().equals("DAWNRISE_RELEASE")?"RELEASE_DETAILS":"ANNOUNCEMENT",row.sourceId()),row.eventType().toLowerCase()+":"+row.sourceId(),OffsetDateTime.now(),recipients)).retrieve().toBodilessEntity();store.published(row.id(),owner);}catch(Exception e){store.failed(row.id(),owner,row.attemptCount()+1,properties.getMaxAttempts(),e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}});}
 private Set<Long> resolve(SchoolNotificationOutboxStore.Row row)throws Exception{List<Audience> audiences=json.readValue(row.audienceSpec(),new TypeReference<List<Audience>>(){});List<Audience> nativeAudience=audiences.stream().filter(a->!Set.of("WING","GRADE","SECTION").contains(a.type())).toList();Set<Long> result=new HashSet<>();if(!nativeAudience.isEmpty()){AudienceResponse r=identity.post().uri("/internal/v1/organizations/{org}/notification-audience/resolve",row.organizationId()).headers(h->headers(h,identityProperties.getApiKey())).body(new AudienceRequest(nativeAudience)).retrieve().body(AudienceResponse.class);if(r!=null)result.addAll(r.userIds());}for(Audience a:audiences)if(Set.of("WING","GRADE","SECTION").contains(a.type())){AcademicResponse r=academic.get().uri("/internal/v1/organizations/{org}/notification-audience/{type}/{id}",row.organizationId(),a.type(),Long.parseLong(a.key())).headers(h->headers(h,properties.getApiKey())).retrieve().body(AcademicResponse.class);if(r!=null)result.addAll(r.studentUserIds());}return result;}
 private void headers(org.springframework.http.HttpHeaders h,String key){h.set("X-Internal-Api-Key",key);h.set("X-Service-Name","school-service");}
 record Audience(String type,String key){} record AudienceRequest(List<Audience> audiences){} record AudienceResponse(Set<Long> userIds){} record AcademicResponse(Set<Long> studentUserIds){} record Action(String type,long referenceId){} record Ingest(long organizationId,String sourceEventId,String eventType,String category,String importance,String heading,String context,Action action,String groupingKey,OffsetDateTime occurredAt,Set<Long> recipientUserIds){}
}
