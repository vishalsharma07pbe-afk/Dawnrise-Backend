package com.dawnrise.identity.notification.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CommunicationIdentityDirectoryPostgresTest {
    private String schema;
    private JdbcClient admin;
    private CommunicationIdentityDirectory directory;

    @BeforeEach void setUp() {
        String baseUrl = System.getenv("COMMUNICATION_TEST_DATABASE_URL");
        assumeTrue(baseUrl != null && !baseUrl.isBlank(), "Disposable PostgreSQL is required");
        assumeTrue("true".equals(System.getenv("COMMUNICATION_TEST_DATABASE_DISPOSABLE")), "Explicit disposable database confirmation is required");
        schema = "communication_identity_" + UUID.randomUUID().toString().replace("-", "");
        admin = JdbcClient.create(new DriverManagerDataSource(baseUrl, "postgres", ""));
        admin.sql("CREATE SCHEMA " + schema).update();
        String separator = baseUrl.contains("?") ? "&" : "?";
        var source = new DriverManagerDataSource(baseUrl + separator + "currentSchema=" + schema, "postgres", "");
        JdbcClient jdbc = JdbcClient.create(source);
        jdbc.sql("CREATE TABLE users(id BIGINT PRIMARY KEY,organization_id BIGINT,status VARCHAR(30),first_name VARCHAR(100),middle_name VARCHAR(100),last_name VARCHAR(100));" +
                "CREATE TABLE student_guardian_relationships(organization_id BIGINT,student_user_id BIGINT,guardian_user_id BIGINT,status VARCHAR(30))").update();
        jdbc.sql("INSERT INTO users VALUES(201,7,'ACTIVE','Active',NULL,'Guardian'),(202,7,'INACTIVE','Inactive',NULL,'Account'),(203,7,'ACTIVE','Ended',NULL,'Link'),(204,8,'ACTIVE','Other',NULL,'Tenant');" +
                "INSERT INTO student_guardian_relationships VALUES(7,100,201,'ACTIVE'),(7,100,201,'ACTIVE'),(7,100,202,'ACTIVE'),(7,100,203,'INACTIVE'),(8,100,204,'ACTIVE')").update();
        directory = new CommunicationIdentityDirectory(new NamedParameterJdbcTemplate(source));
    }

    @AfterEach void cleanUp() { if (admin != null && schema != null) admin.sql("DROP SCHEMA " + schema + " CASCADE").update(); }

    @Test void activeGuardiansAreTenantSafeActiveAndDeduplicated() {
        assertEquals(Set.of(201L), directory.activeGuardians(7, Set.of(100L)));
    }
}
