package com.dawnrise.school.leave;

import com.dawnrise.school.announcement.NotificationRelayProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

@Component
public class AcademicWorkingDayClient {
    private final RestClient client; private final NotificationRelayProperties properties;
    public AcademicWorkingDayClient(@Qualifier("academicNotificationRestClient") RestClient client,
                                    NotificationRelayProperties properties){this.client=client;this.properties=properties;}
    public Days days(long organizationId,LocalDate start,LocalDate end){
        Days value=client.get().uri(builder->builder.path("/internal/v1/organizations/{organizationId}/working-days")
                .queryParam("start",start).queryParam("end",end).build(organizationId))
                .headers(headers->{headers.set("X-Internal-Api-Key",properties.getApiKey());headers.set("X-Service-Name","school-service");})
                .retrieve().body(Days.class);
        if(value==null)throw new IllegalStateException("Academic working-day calendar is unavailable");
        return value;
    }
    public record Days(List<LocalDate> workingDates,List<LocalDate> excludedDates){}
}
