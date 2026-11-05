package com.dawnrise.school.announcement;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;
import java.time.Duration;

@ConfigurationProperties(prefix="dawnrise.notification-relay")
public class NotificationRelayProperties implements InitializingBean {
    private String academicBaseUrl="http://localhost:8082";
    private String apiKey;
    private int maxAttempts=8;
    private Duration lease=Duration.ofMinutes(2);
    public String getAcademicBaseUrl(){return academicBaseUrl;} public void setAcademicBaseUrl(String v){academicBaseUrl=v;}
    public String getApiKey(){return apiKey;} public void setApiKey(String v){apiKey=v;}
    public int getMaxAttempts(){return maxAttempts;} public void setMaxAttempts(int v){maxAttempts=v;}
    public Duration getLease(){return lease;} public void setLease(Duration v){lease=v;}
    @Override public void afterPropertiesSet(){
        if(apiKey==null||apiKey.isBlank())
            throw new IllegalStateException("ACADEMIC_INTERNAL_API_KEY must be configured for school-to-academic requests");
    }
}
