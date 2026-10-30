package com.dawnrise.school.announcement;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.web.client.RestClient;

@Configuration @EnableConfigurationProperties(NotificationRelayProperties.class)
public class NotificationRelayConfig {
 @Bean RestClient academicNotificationRestClient(NotificationRelayProperties p){return RestClient.builder().baseUrl(p.getAcademicBaseUrl()).build();}
}
