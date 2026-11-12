package com.dawnrise.school.communication;

import com.dawnrise.school.announcement.NotificationRelayProperties;
import com.dawnrise.school.school.provisioning.IdentityProvisioningProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CommunicationDirectoryGatewayTest {
    @Test
    void identityAudienceReadsTheEstablishedUserIdsResponseField() {
        RestClient.Builder identityBuilder = RestClient.builder().baseUrl("http://identity-service");
        MockRestServiceServer identityServer = MockRestServiceServer.bindTo(identityBuilder).build();
        IdentityProvisioningProperties identityProperties = new IdentityProvisioningProperties();
        identityProperties.setApiKey("identity-test-key");
        NotificationRelayProperties academicProperties = new NotificationRelayProperties();
        academicProperties.setApiKey("academic-test-key");
        CommunicationDirectoryGateway gateway = new CommunicationDirectoryGateway(
                identityBuilder.build(),
                RestClient.builder().baseUrl("http://academic-service").build(),
                identityProperties,
                academicProperties);

        identityServer.expect(requestTo("http://identity-service/internal/v1/organizations/7/notification-audience/resolve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "identity-test-key"))
                .andExpect(header("X-Service-Name", "school-service"))
                .andRespond(withSuccess("{\"userIds\":[42,50]}", MediaType.APPLICATION_JSON));

        assertEquals(Set.of(42L, 50L), gateway.identityAudience(7, "STAFF", null));
        identityServer.verify();
    }
}
