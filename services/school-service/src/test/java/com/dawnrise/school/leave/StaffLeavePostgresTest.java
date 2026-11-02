package com.dawnrise.school.leave;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StaffLeavePostgresTest {
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        String url=System.getenv("SCHOOL_NOTIFICATION_TEST_DATABASE_URL");
        Assumptions.assumeTrue(url!=null&&url.startsWith("jdbc:postgresql:")&&!url.contains("identity_service_db"));
        var dataSource=new DriverManagerDataSource(url,System.getenv().getOrDefault("SCHOOL_NOTIFICATION_TEST_DATABASE_USER","postgres"),System.getenv().getOrDefault("SCHOOL_NOTIFICATION_TEST_DATABASE_PASSWORD",""));
        Flyway.configure().dataSource(dataSource).load().migrate();jdbc=new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE staff_leave_coverage_delivery_audit,staff_leave_coverage_outbox,staff_leave_policy_audit,staff_leave_balance_adjustments,staff_leave_audit,staff_leave_approvals,staff_leave_requests,staff_leave_approval_rules,staff_leave_balances,staff_leave_types,school_notification_outbox,schools RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO schools(id,school_code,name,email,phone,status) VALUES(1,'ONE','One','one@test.invalid','111','ACTIVE'),(2,'TWO','Two','two@test.invalid','222','ACTIVE')");
        jdbc.update("INSERT INTO staff_leave_types(id,organization_id,code,name,active_from,yearly_allowance,applicable_roles,created_by_user_id) VALUES(10,1,'CASUAL','Casual',CURRENT_DATE,12,ARRAY['TEACHER'],5)");
    }

    @Test void v11MigratesAndTenantForeignKeysRejectCrossTenantRequests(){
        assertThrows(Exception.class,()->jdbc.update("INSERT INTO staff_leave_requests(organization_id,requester_user_id,leave_type_id,request_kind,start_date,end_date,units,reason) VALUES(2,9,10,'FULL_DAY',CURRENT_DATE,CURRENT_DATE,1,'private')"));
    }

    @Test void approvalStepsAreUniqueAndCannotSelfDescribeTheSameRole(){
        jdbc.update("INSERT INTO staff_leave_approval_rules(organization_id,requester_role,step_number,approver_role) VALUES(1,'TEACHER',1,'PRINCIPAL')");
        assertThrows(Exception.class,()->jdbc.update("INSERT INTO staff_leave_approval_rules(organization_id,requester_role,step_number,approver_role) VALUES(1,'TEACHER',1,'HR')"));
        assertThrows(Exception.class,()->jdbc.update("INSERT INTO staff_leave_approval_rules(organization_id,requester_role,step_number,approver_role) VALUES(1,'HR',1,'HR')"));
    }

    @Test void requestAndBalanceAuditRowsAreImmutable(){
        jdbc.update("INSERT INTO staff_leave_balances(id,organization_id,staff_user_id,leave_type_id,leave_year,accrued) VALUES(20,1,9,10,2026,12)");
        jdbc.update("INSERT INTO staff_leave_balance_adjustments(organization_id,balance_id,amount,reason,actor_user_id) VALUES(1,20,1,'Correction',5)");
        assertThrows(Exception.class,()->jdbc.update("UPDATE staff_leave_balance_adjustments SET reason='Changed' WHERE balance_id=20"));
    }

    @Test void coverageOutboxDeduplicatesSourceEventsAndSupportsLeaseRecovery(){
        jdbc.update("INSERT INTO staff_leave_requests(id,organization_id,requester_user_id,leave_type_id,request_kind,start_date,end_date,units,reason) VALUES(30,1,9,10,'FULL_DAY',CURRENT_DATE,CURRENT_DATE,1,'private')");
        jdbc.update("INSERT INTO staff_leave_coverage_outbox(organization_id,leave_request_id,operation,source_event_id,actor_user_id,payload,lease_owner,lease_until) VALUES(1,30,'UPSERT','leave:30:1',5,'{}','11111111-1111-1111-1111-111111111111',CURRENT_TIMESTAMP-INTERVAL '1 minute')");
        assertThrows(Exception.class,()->jdbc.update("INSERT INTO staff_leave_coverage_outbox(organization_id,leave_request_id,operation,source_event_id,actor_user_id,payload) VALUES(1,30,'UPSERT','leave:30:1',5,'{}')"));
        assertEquals(1,jdbc.update("UPDATE staff_leave_coverage_outbox SET lease_owner=NULL,lease_until=NULL WHERE status='PENDING' AND lease_until<CURRENT_TIMESTAMP"));
    }
}
