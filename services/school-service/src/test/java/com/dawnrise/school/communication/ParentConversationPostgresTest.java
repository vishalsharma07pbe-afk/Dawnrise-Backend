package com.dawnrise.school.communication;

import com.dawnrise.school.leave.AcademicWorkingDayClient;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParentConversationPostgresTest {
    private NamedParameterJdbcTemplate jdbc;
    private ParentConversationService service;
    private CommunicationDirectoryGateway directory;

    @BeforeEach void setUp() throws Exception {
        String url=System.getenv("SCHOOL_NOTIFICATION_TEST_DATABASE_URL");
        Assumptions.assumeTrue(explicit(url));
        var data=new DriverManagerDataSource(url,System.getenv().getOrDefault("SCHOOL_NOTIFICATION_TEST_DATABASE_USER","postgres"),System.getenv().getOrDefault("SCHOOL_NOTIFICATION_TEST_DATABASE_PASSWORD",""));
        Flyway.configure().dataSource(data).load().migrate();
        jdbc=new NamedParameterJdbcTemplate(data);
        jdbc.getJdbcTemplate().execute("TRUNCATE parent_communication_audit,parent_communication_messages,parent_communication_participants,parent_communication_threads,parent_communication_contacts,parent_communication_policies,school_notification_outbox,schools RESTART IDENTITY CASCADE");
        jdbc.getJdbcTemplate().update("INSERT INTO schools(id,school_code,name,email,phone,status,time_zone_id) VALUES(1,'ONE','One','one@test.invalid','111','ACTIVE','Asia/Kolkata'),(2,'TWO','Two','two@test.invalid','222','ACTIVE','Asia/Kolkata')");
        directory=mock(CommunicationDirectoryGateway.class);
        when(directory.guardians(eq(1L),eq(Set.of(101L)))).thenReturn(Set.of(201L));
        when(directory.parentContacts(1,101)).thenReturn(List.of(new CommunicationDirectoryGateway.ParentContact(301,"CLASS_TEACHER")));
        when(directory.searchUsers(eq(1L),anyString(),anyInt(),anyInt(),anySet())).thenAnswer(invocation->{
            Set<Long> ids=invocation.getArgument(4);
            var users=ids.stream().map(id->new CommunicationDirectoryGateway.IdentityUser(id,id==301?"Assigned Teacher":"Wellbeing Officer",List.of("TEACHER"))).toList();
            return new CommunicationDirectoryGateway.IdentityPage(users,0,50,users.size());
        });
        when(directory.resolveUsers(eq(1L),anySet())).thenAnswer(invocation->invocation.getArgument(1));
        when(directory.senderAuthority(eq(1L),anyLong())).thenReturn(new CommunicationDirectoryGateway.Authority(true,Set.of()));
        var json=mock(ObjectMapper.class);when(json.writeValueAsString(any())).thenReturn("[301]");
        var calendar=mock(AcademicWorkingDayClient.class);when(calendar.days(eq(1L),any(),any())).thenReturn(new AcademicWorkingDayClient.Days(List.of(LocalDate.now().plusDays(1),LocalDate.now().plusDays(2),LocalDate.now().plusDays(3)),List.of()));
        service=new ParentConversationService(jdbc,directory,json,calendar);
    }

    @Test void exposesOnlyAssignedAndConfiguredContactsAndCreatesParticipantSafeThread(){
        jdbc.getJdbcTemplate().update("INSERT INTO parent_communication_contacts(organization_id,user_id,category,contact_type,display_label,created_by_user_id) VALUES(1,302,'WELLBEING','PARENT_FACING','Wellbeing',900)");
        var contacts=service.contacts(1,201,101,"");
        assertEquals(Set.of(301L,302L),contacts.stream().map(ParentConversationDtos.Contact::userId).collect(java.util.stream.Collectors.toSet()));
        var thread=service.create(1,201,"create-thread-001",new ParentConversationDtos.Create(101,301,"ACADEMIC","Homework","Please clarify the homework."));
        assertEquals(0,thread.unreadCount());
        assertEquals(1,jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM school_notification_outbox WHERE source_type='CONVERSATION'",Integer.class));
        assertThrows(Exception.class,()->service.get(2,201,thread.id()));
    }

    @Test void enforcesConfiguredCategoryOpenLimitAndDuplicateSendProtection(){
        jdbc.getJdbcTemplate().update("INSERT INTO parent_communication_contacts(organization_id,user_id,category,contact_type,display_label,created_by_user_id) VALUES(1,302,'WELLBEING','PARENT_FACING','Wellbeing',900)");
        jdbc.getJdbcTemplate().update("INSERT INTO parent_communication_policies(organization_id,max_open_threads,max_new_threads_7_days,head_escalation_school_days,updated_by_user_id) VALUES(1,1,5,2,900)");
        assertThrows(IllegalArgumentException.class,()->service.create(1,201,"wrong-category-1",new ParentConversationDtos.Create(101,302,"FEES","Question","Please help.")));
        var thread=service.create(1,201,"create-thread-002",new ParentConversationDtos.Create(101,302,"WELLBEING","Support","Please help."));
        service.reply(1,201,thread.id(),"reply-message-001","Additional context.");
        service.reply(1,201,thread.id(),"reply-message-001","Additional context.");
        assertEquals(2,jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM parent_communication_messages",Integer.class));
        assertThrows(IllegalStateException.class,()->service.create(1,201,"create-thread-003",new ParentConversationDtos.Create(101,301,"GENERAL","Another","Another question.")));
    }

    @Test void directStaffConversationIsVisibleAndReplyableForBothParticipants(){
        var thread=service.createDirect(1,301,true,"direct-thread-001",new ParentConversationDtos.CreateDirect(302,"Private question","Can you review this?"));
        assertEquals("STAFF_DIRECT",thread.threadKind());
        assertEquals(1,service.list(1,302).size());
        var replied=service.reply(1,302,thread.id(),"direct-reply-001","Yes, I will review it.");
        assertEquals(2,replied.messages().size());
        assertEquals(2,jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM school_notification_outbox WHERE source_type='CONVERSATION'",Integer.class));
    }

    private boolean explicit(String url){if(url==null||!url.startsWith("jdbc:postgresql://"))return false;try{URI value=URI.create(url.substring(5));return Set.of("127.0.0.1","localhost").contains(value.getHost())&&value.getPath().matches("/dawnrise_school_notification_test_[A-Za-z0-9_]+");}catch(Exception ignored){return false;}}
}
