package com.dawnrise.identity.notification.sms;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(NotificationSmsProperties.class)
public class NotificationSmsConfig {
    @Bean
    @ConditionalOnProperty(prefix = "dawnrise.notification-sms", name = "enabled", havingValue = "true")
    RestClient msg91RestClient(NotificationSmsProperties properties) {
        return RestClient.builder().baseUrl(properties.getBaseUrl()).build();
    }
}
