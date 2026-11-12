package com.dawnrise.school.communication;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunicationNoticePostgresTest {
    private NamedParameterJdbcTemplate jdbc;
    private CommunicationNoticeService service;

    @BeforeEach
    void setUp() throws Exception {
        String url=System.getenv("SCHOOL_NOTIFICATION_TEST_DATABASE_URL");
        Assumptions.assumeTrue(explicit(url));
        var data=new DriverManagerDataSource(url,System.getenv().getOrDefault("SCHOOL_NOTIFICATION_TEST_DATABASE_USER","postgres"),System.getenv().getOrDefault("SCHOOL_NOTIFICATION_TEST_DATABASE_PASSWORD",""));
        Flyway.configure().dataSource(data).load().migrate();
        jdbc=new NamedParameterJdbcTemplate(data);
        jdbc.getJdbcTemplate().execute("TRUNCATE communication_notice_mutations,school_notification_outbox,school_announcement_audit,school_announcement_audiences,school_announcements,schools RESTART IDENTITY CASCADE");
        jdbc.getJdbcTemplate().update("INSERT INTO schools(id,school_code,name,email,phone,status,time_zone_id) VALUES(1,'ONE','One','one@test.invalid','111','ACTIVE','Asia/Kolkata'),(2,'TWO','Two','two@test.invalid','222','ACTIVE','Asia/Kolkata')");
        var audiences=mock(CommunicationAudienceService.class);
        when(audiences.preview(any(Long.class),any(Long.class),any(Boolean.class),any())).thenReturn(new CommunicationDtos.Preview(List.of(new CommunicationDtos.Selection(CommunicationDtos.Type.SECTION,"10")),2,"AT_DELIVERY_TIME","Membership resolves at delivery time."));
        var json=mock(ObjectMapper.class);
        when(json.writeValueAsString(any())).thenReturn("[{\"type\":\"SECTION\",\"key\":\"10\"}]");
        service=new CommunicationNoticeService(jdbc,json,audiences);
    }

    @Test
    void createAndPublishAreIdempotentAndHistoryIsOwnerScoped(){
        var input=new CommunicationDtos.CreateNotice("Trip","Bring lunch",List.of(new CommunicationDtos.Selection(CommunicationDtos.Type.SECTION,"10")));
        var first=service.create(1,50,false,"create-key-123",input);
        assertEquals(first.id(),service.create(1,50,false,"create-key-123",input).id());
        assertThrows(IllegalStateException.class,()->service.create(1,50,false,"create-key-123",new CommunicationDtos.CreateNotice("Different","Body",input.selections())));
        var published=service.publish(1,50,false,first.id(),"publish-key-123",first.version());
        assertEquals(published.id(),service.publish(1,50,false,first.id(),"publish-key-123",first.version()).id());
        assertEquals(1,jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM school_notification_outbox",Integer.class));
        assertEquals(1,service.list(1,50,0,20).total());
        assertEquals(0,service.list(1,51,0,20).total());
        assertThrows(Exception.class,()->service.get(2,50,first.id()));
    }

    @Test
    void acceptedScheduledDeliveryCannotBeCancelled(){
        var input=new CommunicationDtos.CreateNotice("Trip","Bring lunch",List.of(new CommunicationDtos.Selection(CommunicationDtos.Type.SECTION,"10")));
        var draft=service.create(1,50,false,"create-key-456",input);
        var scheduled=service.schedule(1,50,false,draft.id(),"schedule-key-456",new CommunicationDtos.ScheduleMutation(draft.version(),LocalDateTime.now().plusDays(1)));
        jdbc.getJdbcTemplate().update("UPDATE school_notification_outbox SET recipient_user_ids='[70,71]',accepted_recipient_count=2,recipient_set_accepted_at=CURRENT_TIMESTAMP WHERE source_id=?",scheduled.id());
        assertThrows(IllegalStateException.class,()->service.cancel(1,50,scheduled.id(),"cancel-key-456",scheduled.version()));
        assertTrue(service.get(1,50,scheduled.id()).status().equals("SCHEDULED"));
    }

    private boolean explicit(String url){if(url==null||!url.startsWith("jdbc:postgresql://"))return false;try{URI value=URI.create(url.substring(5));return Set.of("127.0.0.1","localhost").contains(value.getHost())&&value.getPath().matches("/dawnrise_school_notification_test_[A-Za-z0-9_]+");}catch(Exception ignored){return false;}}
}
