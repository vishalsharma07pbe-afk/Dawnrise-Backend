package com.dawnrise.academic.notificationdelivery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CommunicationAcademicDirectoryPostgresTest {
    private String baseUrl;
    private String schema;
    private JdbcClient admin;
    private CommunicationAcademicDirectory directory;

    @BeforeEach void setUp() {
        baseUrl = System.getenv("COMMUNICATION_TEST_DATABASE_URL");
        assumeTrue(baseUrl != null && !baseUrl.isBlank(), "Disposable PostgreSQL is required");
        assumeTrue("true".equals(System.getenv("COMMUNICATION_TEST_DATABASE_DISPOSABLE")), "Explicit disposable database confirmation is required");
        schema = "communication_scope_" + UUID.randomUUID().toString().replace("-", "");
        admin = JdbcClient.create(new DriverManagerDataSource(baseUrl, "postgres", ""));
        admin.sql("CREATE SCHEMA " + schema).update();
        String separator = baseUrl.contains("?") ? "&" : "?";
        JdbcClient jdbc = JdbcClient.create(new DriverManagerDataSource(baseUrl + separator + "currentSchema=" + schema, "postgres", ""));
        jdbc.sql("CREATE TABLE academic_years(id BIGINT PRIMARY KEY,organization_id BIGINT,status VARCHAR(20));" +
                "CREATE TABLE grade_levels(id BIGINT PRIMARY KEY,organization_id BIGINT,academic_year_id BIGINT,name VARCHAR(100),display_order INT);" +
                "CREATE TABLE sections(id BIGINT PRIMARY KEY,organization_id BIGINT,academic_year_id BIGINT,grade_level_id BIGINT,name VARCHAR(100),display_order INT);" +
                "CREATE TABLE timetable_wings(id BIGINT PRIMARY KEY,organization_id BIGINT,name VARCHAR(120));" +
                "CREATE TABLE timetable_grade_wings(organization_id BIGINT,academic_year_id BIGINT,grade_level_id BIGINT,wing_id BIGINT);" +
                "CREATE TABLE teacher_assignments(organization_id BIGINT,academic_year_id BIGINT,section_id BIGINT,teacher_user_id BIGINT);" +
                "CREATE TABLE student_enrollments(organization_id BIGINT,academic_year_id BIGINT,grade_level_id BIGINT,section_id BIGINT,student_user_id BIGINT,status VARCHAR(20))").update();
        jdbc.sql("INSERT INTO academic_years VALUES(1,7,'ACTIVE');" +
                "INSERT INTO grade_levels VALUES(20,7,1,'Grade 4',1);" +
                "INSERT INTO sections VALUES(10,7,1,20,'A',1),(11,7,1,20,'B',2);" +
                "INSERT INTO timetable_wings VALUES(30,7,'Primary');" +
                "INSERT INTO timetable_grade_wings VALUES(7,1,20,30);" +
                "INSERT INTO teacher_assignments VALUES(7,1,10,42);" +
                "INSERT INTO student_enrollments VALUES(7,1,20,10,100,'ENROLLED'),(7,1,20,11,101,'ENROLLED'),(7,1,20,10,102,'WITHDRAWN')").update();
        directory = new CommunicationAcademicDirectory(jdbc, new AcademicAudienceResolver(jdbc));
    }

    @AfterEach void cleanUp() {
        if (admin != null && schema != null) admin.sql("DROP SCHEMA " + schema + " CASCADE").update();
    }

    @Test void gradeAndWingResolveOnlyStudentsFromAssignedSections() {
        assertEquals(Set.of(100L), directory.resolve(7, new CommunicationAcademicDtos.ResolveRequest(42, false, "GRADE", 20)));
        assertEquals(Set.of(100L), directory.resolve(7, new CommunicationAcademicDtos.ResolveRequest(42, false, "WING", 30)));
    }

    @Test void rejectsDirectUnassignedSectionTampering() {
        assertThrows(IllegalArgumentException.class, () -> directory.resolve(7, new CommunicationAcademicDtos.ResolveRequest(42, false, "SECTION", 11)));
    }
}
