package com.dawnrise.school.announcement;

import com.dawnrise.school.communication.CommunicationAudienceService;
import com.dawnrise.school.communication.CommunicationDirectoryGateway;
import com.dawnrise.school.school.provisioning.IdentityProvisioningProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchoolNotificationRelayWorkerTest {
    @Test
    void failsClosedWhenScheduledSenderLostPermissionBeforeFirstDelivery(){
        var store=mock(SchoolNotificationOutboxStore.class);
        var identity=mock(RestClient.class);
        var academic=mock(RestClient.class);
        var directory=mock(CommunicationDirectoryGateway.class);
        var audiences=mock(CommunicationAudienceService.class);
        var row=new SchoolNotificationOutboxStore.Row(1,7,"communication-notice:9","SCHOOL_ANNOUNCEMENT","NORMAL","Heading","Context","[{\"type\":\"SECTION\",\"key\":\"10\"}]","[]",9,0,42L,false,null);
        when(store.claim(any(UUID.class),any(Duration.class))).thenReturn(Optional.of(row));
        when(directory.senderAuthority(7,42)).thenReturn(new CommunicationDirectoryGateway.Authority(true,Set.of()));
        var relay=new NotificationRelayProperties();relay.setApiKey("test-key");
        var identityProperties=new IdentityProvisioningProperties();identityProperties.setApiKey("test-key");
        new SchoolNotificationRelayWorker(store,identity,academic,identityProperties,relay,new ObjectMapper(),audiences,directory).deliver();
        verify(store).failed(anyLong(),any(UUID.class),anyInt(),anyInt(),contains("no longer authorized"));
        verify(audiences,never()).resolveRecipients(anyLong(),anyLong(),any(Boolean.class),any());
    }
}
