package com.dawnrise.identity.notification.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "dawnrise.notification-sms", name = "enabled", havingValue = "true")
public class Msg91SmsClient {
    private final RestClient client;
    private final NotificationSmsProperties properties;

    public Msg91SmsClient(RestClient msg91RestClient, NotificationSmsProperties properties) {
        this.client = msg91RestClient;
        this.properties = properties;
    }

    public String send(String templateId, String phone, Map<String, String> variables) {
        Map<String, Object> recipient = new java.util.LinkedHashMap<>(variables);
        recipient.put("mobiles", phone.substring(1));
        Response response = client.post().uri("/api/v5/flow")
                .header("authkey", properties.getAuthKey())
                .body(Map.of("template_id", templateId, "short_url", "0",
                        "realTimeResponse", "1", "recipients", List.of(recipient)))
                .retrieve().body(Response.class);
        if (response == null || !"success".equalsIgnoreCase(response.type())
                || response.message() == null || response.message().isBlank()) {
            throw new IllegalStateException("MSG91 rejected the SMS request");
        }
        return response.message();
    }

    record Response(String type, String message) {}
}
