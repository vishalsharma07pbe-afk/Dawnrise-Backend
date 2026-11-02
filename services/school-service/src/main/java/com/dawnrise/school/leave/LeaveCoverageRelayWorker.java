package com.dawnrise.school.leave;

import com.dawnrise.school.announcement.NotificationRelayProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class LeaveCoverageRelayWorker {
    private final LeaveCoverageOutboxStore store; private final RestClient academic;
    private final NotificationRelayProperties properties; private final ObjectMapper json;
    public LeaveCoverageRelayWorker(LeaveCoverageOutboxStore store,
            @Qualifier("academicNotificationRestClient") RestClient academic,
            NotificationRelayProperties properties,ObjectMapper json){this.store=store;this.academic=academic;this.properties=properties;this.json=json;}
    @Scheduled(fixedDelayString="${dawnrise.notification-relay.leave-coverage-delay:PT10S}")
    public void deliver(){UUID owner=UUID.randomUUID();store.claim(owner,properties.getLease()).ifPresent(row->{try{
        Map<String,Object> payload=new HashMap<>(json.readValue(row.payload(),new TypeReference<Map<String,Object>>(){}));
        payload.put("operation",row.operation());payload.put("actorUserId",row.actorUserId());
        academic.post().uri("/internal/v1/organizations/{organizationId}/staff-leave-coverage",row.organizationId())
                .headers(headers->{headers.set("X-Internal-Api-Key",properties.getApiKey());headers.set("X-Service-Name","school-service");})
                .body(payload).retrieve().toBodilessEntity();store.published(row.id(),owner);
    }catch(Exception exception){store.failed(row.id(),owner,row.attemptCount()+1,properties.getMaxAttempts(),exception.getMessage());}});}
}
