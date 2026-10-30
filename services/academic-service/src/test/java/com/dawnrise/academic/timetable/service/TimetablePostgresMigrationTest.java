package com.dawnrise.academic.timetable.service;

import org.flywaydb.core.Flyway;
import com.dawnrise.academic.teacherassignment.integration.identity.IdentityTeacherEligibilityClient;
import com.dawnrise.academic.teacherassignment.integration.identity.BatchTeachingEligibilityResponse;
import com.dawnrise.academic.teacherassignment.integration.identity.TeachingEligibilityResponse;
import com.dawnrise.academic.teacherassignment.integration.identity.TeachingEligibilityReason;
import com.dawnrise.academic.results.integration.identity.IdentityGuardianAccessClient;
import com.dawnrise.academic.common.integration.school.SchoolTimeZoneClient;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.ActivityType;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.CreateDraftRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.LessonRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.SwapLessonsRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.EffectiveDateIntentRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.WorkflowStatus;
import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.AvailabilityType;
import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.TeacherAvailabilityUpdateRequest;
import com.dawnrise.academic.timetable.dto.TimetableGenerationDtos.GenerateRequest;
import com.dawnrise.academic.timetable.dto.TimetableGenerationDtos.JobStatus;
import com.dawnrise.academic.timetable.exception.TimetableConfigurationConflictException;
import com.dawnrise.academic.dailycoverage.service.DailyCoverageService;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.AbsenceType;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.CandidateChoiceRequest;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.RecordAbsenceRequest;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.VersionRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.Set;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimetablePostgresMigrationTest {
    private Path temporaryDirectory;
    private Path pgCtl;
    private Path dataDirectory;
    private Path socketDirectory;
    private String jdbcUrl;

    @BeforeAll
    void migrateIntoDisposablePostgres() throws Exception {
        temporaryDirectory = Files.createTempDirectory("dawnrise-timetable-postgres-");
        Path initDb = executable("initdb");
        pgCtl = executable("pg_ctl");
        assumeTrue(initDb != null && pgCtl != null,
                "PostgreSQL binaries are required for the migration integration test");
        dataDirectory = temporaryDirectory.resolve("postgres-data");
        run(initDb.toString(), "-D", dataDirectory.toString(), "-A", "trust", "-U", "postgres",
                "--no-locale", "--encoding=UTF8");
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        socketDirectory = Files.createDirectory(Path.of("/tmp", "drtt-" + port));
        run(pgCtl.toString(), "-D", dataDirectory.toString(), "-l", temporaryDirectory.resolve("postgres.log").toString(),
                "-o", "-p " + port + " -k " + socketDirectory + " -F", "-w", "start");
        jdbcUrl = "jdbc:postgresql://127.0.0.1:" + port + "/postgres";
        Flyway.configure().dataSource(jdbcUrl, "postgres", "")
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
    }

    @AfterAll
    void stopDisposablePostgres() throws Exception {
        if (pgCtl != null && dataDirectory != null && Files.exists(dataDirectory.resolve("postmaster.pid"))) {
            run(pgCtl.toString(), "-D", dataDirectory.toString(), "-m", "immediate", "-w", "stop");
        }
        if (temporaryDirectory != null && Files.exists(temporaryDirectory)) {
            try (var paths = Files.walk(temporaryDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                });
            }
        }
        if (socketDirectory != null) Files.deleteIfExists(socketDirectory);
    }

    @Test
    void exclusionRejectsOverlapAndSubMinuteTimesButAllowsAdjacency() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO timetable_bell_schedules(id,organization_id,name) VALUES (1,1,'Standard')");
            statement.execute("""
                    INSERT INTO timetable_bell_schedule_days
                    (id,organization_id,bell_schedule_id,day_of_week,working_day,day_start_time,day_end_time)
                    VALUES (1,1,1,1,true,'09:00','12:00')
                    """);
            statement.execute(intervalSql(1, "Period 1", "09:00", "09:30", 1));

            SQLException overlap = assertThrows(SQLException.class,
                    () -> statement.execute(intervalSql(2, "Overlap", "09:29", "09:31", 2)));
            assertEquals("23P01", overlap.getSQLState());

            SQLException subMinute = assertThrows(SQLException.class,
                    () -> statement.execute(intervalSql(3, "Sub-minute", "09:30:30", "09:45", 3)));
            assertEquals("23514", subMinute.getSQLState());

            assertDoesNotThrow(() -> statement.execute(intervalSql(4, "Adjacent", "09:30", "10:00", 4)));
        }
    }

    @Test
    void databaseRejectsWingDisagreementAndProtectsLaterRemapping() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            seedAcademicStructure(statement);
            statement.execute("INSERT INTO timetable_wings(id,organization_id,code,name,display_order) VALUES (10,1,'JR','Junior',1),(11,1,'SR','Senior',2)");
            statement.execute("INSERT INTO timetable_grade_wings(organization_id,academic_year_id,grade_level_id,wing_id) VALUES (1,1,1,10)");
            statement.execute("INSERT INTO timetable_bell_schedules(id,organization_id,name) VALUES (10,1,'Junior schedule')");

            SQLException override = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO timetable_bell_schedule_assignments
                    (organization_id,academic_year_id,wing_id,grade_level_id,bell_schedule_id)
                    VALUES (1,1,11,1,10)
                    """));
            assertEquals("23514", override.getSQLState());

            SQLException eligibility = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO timetable_alternate_teacher_eligibilities
                    (organization_id,academic_year_id,teacher_user_id,grade_level_subject_id,grade_level_id,
                     section_id,wing_id,eligibility_type,approved_by_user_id)
                    VALUES (1,1,50,1,1,1,11,'ALTERNATE',99)
                    """));
            assertEquals("23514", eligibility.getSQLState());

            statement.execute("""
                    INSERT INTO timetable_alternate_teacher_eligibilities
                    (organization_id,academic_year_id,teacher_user_id,grade_level_subject_id,grade_level_id,
                     section_id,wing_id,eligibility_type,approved_by_user_id)
                    VALUES (1,1,50,1,1,1,10,'ALTERNATE',99)
                    """);
            SQLException remap = assertThrows(SQLException.class,
                    () -> statement.execute("UPDATE timetable_grade_wings SET wing_id=11 WHERE organization_id=1 AND academic_year_id=1 AND grade_level_id=1"));
            assertEquals("23514", remap.getSQLState());
        }
    }

    @Test
    void availabilityHasNoPrecedenceAndRejectsEveryOverlap() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO academic_years(id,organization_id,name,start_date,end_date) VALUES (20,2,'2026','2026-01-01','2026-12-31')");
            statement.execute("""
                    INSERT INTO timetable_teacher_availability
                    (organization_id,academic_year_id,teacher_user_id,day_of_week,start_time,end_time,availability_type)
                    VALUES (2,20,70,1,'09:00','10:00','AVAILABLE')
                    """);
            SQLException contradictory = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO timetable_teacher_availability
                    (organization_id,academic_year_id,teacher_user_id,day_of_week,start_time,end_time,availability_type)
                    VALUES (2,20,70,1,'09:30','10:30','UNAVAILABLE')
                    """));
            assertEquals("23P01", contradictory.getSQLState());
            assertDoesNotThrow(() -> statement.execute("""
                    INSERT INTO timetable_teacher_availability
                    (organization_id,academic_year_id,teacher_user_id,day_of_week,start_time,end_time,availability_type)
                    VALUES (2,20,70,1,'10:00','11:00','DISCOURAGED')
                    """));
        }
    }

    @Test
    void draftListingIntentAndHistoryAreTenantScopedAndOptimistic() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO academic_years(id,organization_id,name,start_date,end_date) VALUES (22,2,'2027','2027-01-01','2027-12-31')");
            statement.execute("INSERT INTO timetable_drafts(id,organization_id,academic_year_id,name,created_by_user_id,updated_by_user_id) VALUES (22,2,22,'Manual',80,80),(23,2,22,'Generated',80,80)");
            statement.execute("INSERT INTO timetable_generation_jobs(id,organization_id,academic_year_id,request_key,status,proposal_count,completed_proposals,seed,solve_time_seconds,created_by_user_id) VALUES (22,2,22,'list-test','COMPLETED',1,1,42,10,80)");
            statement.execute("INSERT INTO timetable_generation_proposals(organization_id,academic_year_id,job_id,proposal_index,draft_id,feasible,hard_score,soft_score) VALUES (2,22,22,1,23,true,0,5)");
            statement.execute("INSERT INTO timetable_draft_audit(organization_id,academic_year_id,draft_id,actor_user_id,action) VALUES (2,22,22,80,'CREATE_DRAFT')");
        }
        DriverManagerDataSource source = new DriverManagerDataSource(jdbcUrl,"postgres","");
        JdbcClient client = JdbcClient.create(source);
        JdbcTransactionManager manager = new JdbcTransactionManager(source);
        TimetableDraftService service = new TimetableDraftService(client, mock(TimetableConfigurationService.class), manager);
        TransactionTemplate transaction = new TransactionTemplate(manager);
        var page = transaction.execute(status -> service.list(2,22,null,0,10));
        assertEquals(2,page.totalElements());
        assertEquals(com.dawnrise.academic.timetable.dto.TimetableDraftDtos.DraftSource.GENERATED,
                page.content().stream().filter(item -> item.id()==23).findFirst().orElseThrow().source());
        assertThrows(com.dawnrise.academic.timetable.exception.TimetableConfigurationNotFoundException.class,
                () -> transaction.execute(status -> service.list(3,22,null,0,10)));
        var updated = transaction.execute(status -> service.updateIntent(2,81,22,
                new EffectiveDateIntentRequest(LocalDate.of(2027,1,1),LocalDate.of(2027,7,1),0)));
        assertEquals(1,updated.version());
        assertThrows(TimetableConfigurationConflictException.class, () -> transaction.execute(status ->
                service.updateIntent(2,81,22,new EffectiveDateIntentRequest(LocalDate.of(2027,2,1),null,0))));
        var history = transaction.execute(status -> service.history(2,22));
        assertEquals(List.of("CREATE_DRAFT","UPDATE_INTENT"), history.edits().stream().map(event -> event.action()).toList());
        assertEquals(0, service.list(2,22,WorkflowStatus.APPROVED,0,10).totalElements());

        client.sql("""
                INSERT INTO timetable_teacher_availability
                (organization_id,academic_year_id,teacher_user_id,day_of_week,start_time,end_time,availability_type)
                VALUES (2,22,800,1,'09:00','10:00','AVAILABLE')
                """).update();
        TimetableConfigurationService configuration = new TimetableConfigurationService(client,
                mock(IdentityTeacherEligibilityClient.class), org -> java.time.ZoneId.of("Asia/Kolkata"));
        long availabilityId = client.sql("SELECT id FROM timetable_teacher_availability WHERE organization_id=2 AND academic_year_id=22").query(Long.class).single();
        var availability = transaction.execute(status -> configuration.updateTeacherAvailability(2,81,availabilityId,
                new TeacherAvailabilityUpdateRequest(1,java.time.LocalTime.of(10,0),java.time.LocalTime.of(11,0),
                        AvailabilityType.PREFERRED,"Planning",0)));
        assertEquals(1,availability.version());
        assertThrows(TimetableConfigurationConflictException.class, () -> transaction.executeWithoutResult(status ->
                configuration.deleteTeacherAvailability(2,81,availabilityId,0)));
        transaction.executeWithoutResult(status -> configuration.deleteTeacherAvailability(2,81,availabilityId,1));
        assertEquals(0,client.sql("SELECT COUNT(*) FROM timetable_teacher_availability WHERE id=:id")
                .param("id",availabilityId).query(Long.class).single());
        client.sql("""
                INSERT INTO timetable_teacher_policies
                (organization_id,academic_year_id,teacher_user_id,maximum_periods_per_day,
                 maximum_periods_per_week,maximum_consecutive_periods)
                VALUES (2,22,800,5,25,3),(2,22,801,5,25,3)
                """).update();
        IdentityTeacherEligibilityClient identities = mock(IdentityTeacherEligibilityClient.class);
        org.mockito.Mockito.when(identities.checkBatch(2, List.of(800L,801L))).thenReturn(
                new BatchTeachingEligibilityResponse(List.of(
                        new TeachingEligibilityResponse(800L,2L,"Valid Teacher",true,TeachingEligibilityReason.ELIGIBLE),
                        new TeachingEligibilityResponse(801L,99L,"Wrong Tenant",true,TeachingEligibilityReason.ELIGIBLE))));
        TimetableConfigurationService labeledConfiguration = new TimetableConfigurationService(client,identities,
                org -> java.time.ZoneId.of("Asia/Kolkata"));
        var options = transaction.execute(status -> labeledConfiguration.configurationOptions(2,22));
        assertEquals(List.of(800L),options.teachers().stream().map(item -> item.id()).toList());
    }

    @Test
    void draftConstraintsPreventTeacherRoomSectionAndTenantRaces() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO academic_years(id,organization_id,name,start_date,end_date) VALUES (30,3,'2026','2026-01-01','2026-12-31')");
            statement.execute("INSERT INTO grade_levels(id,organization_id,academic_year_id,code,name,display_order) VALUES (30,3,30,'G1','Grade 1',1)");
            statement.execute("INSERT INTO sections(id,organization_id,academic_year_id,grade_level_id,code,name,display_order) VALUES (30,3,30,30,'A','Section A',1),(31,3,30,30,'B','Section B',2)");
            statement.execute("INSERT INTO subjects(id,organization_id,academic_year_id,code,name) VALUES (30,3,30,'MAT','Mathematics')");
            statement.execute("INSERT INTO grade_level_subjects(id,organization_id,academic_year_id,grade_level_id,subject_id,display_order) VALUES (30,3,30,30,30,1)");
            statement.execute("INSERT INTO timetable_wings(id,organization_id,code,name,display_order) VALUES (30,3,'JR','Junior',1)");
            statement.execute("INSERT INTO timetable_grade_wings(organization_id,academic_year_id,grade_level_id,wing_id) VALUES (3,30,30,30)");
            statement.execute("INSERT INTO timetable_bell_schedules(id,organization_id,name) VALUES (30,3,'Standard')");
            statement.execute("INSERT INTO timetable_bell_schedule_days(id,organization_id,bell_schedule_id,day_of_week,working_day,day_start_time,day_end_time) VALUES (30,3,30,1,true,'09:00','12:00')");
            statement.execute("INSERT INTO timetable_bell_intervals(id,organization_id,bell_schedule_day_id,interval_type,name,start_time,end_time,display_order,coverage_mode,assignee_requirement,schedulable) VALUES (30,3,30,'TEACHING','Period 1','09:00','09:40',1,'SECTION','QUALIFIED_SUBJECT_TEACHER',true)");
            statement.execute("INSERT INTO timetable_bell_schedule_assignments(organization_id,academic_year_id,wing_id,bell_schedule_id) VALUES (3,30,30,30)");
            statement.execute("INSERT INTO timetable_rooms(id,organization_id,wing_id,code,name,exclusive_use) VALUES (30,3,30,'LAB','Lab',true)");
            statement.execute("INSERT INTO timetable_drafts(id,organization_id,academic_year_id,name,created_by_user_id,updated_by_user_id) VALUES (30,3,30,'Draft A',90,90)");
            statement.execute(draftSlotSql(30, 30, 500, 30));

            SQLException teacher = assertThrows(SQLException.class,
                    () -> statement.execute(draftSlotSql(31, 31, 500, null)));
            assertEquals("23P01", teacher.getSQLState());
            SQLException room = assertThrows(SQLException.class,
                    () -> statement.execute(draftSlotSql(32, 31, 501, 30)));
            assertEquals("23P01", room.getSQLState());
            SQLException section = assertThrows(SQLException.class,
                    () -> statement.execute(draftSlotSql(33, 30, 501, null)));
            assertTrue(Set.of("23505", "23P01").contains(section.getSQLState()));
            SQLException tenant = assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO timetable_drafts(organization_id,academic_year_id,name,created_by_user_id,updated_by_user_id) VALUES (4,30,'Cross tenant',90,90)"));
            assertEquals("23503", tenant.getSQLState());

            assertEquals(0, statement.executeUpdate("UPDATE timetable_drafts SET version=version+1 WHERE id=30 AND organization_id=3 AND version=7"));
            assertEquals(1, statement.executeUpdate("UPDATE timetable_drafts SET version=version+1 WHERE id=30 AND organization_id=3 AND version=0"));
        }
    }

    @Test
    void generationJobsAreTenantScopedIdempotentAndSingleActivePerYear() throws Exception {
        try(Connection connection=connection();Statement statement=connection.createStatement()){
            statement.execute("INSERT INTO academic_years(id,organization_id,name,start_date,end_date) VALUES (60,6,'2026','2026-01-01','2026-12-31')");
            statement.execute("INSERT INTO timetable_generation_jobs(id,organization_id,academic_year_id,request_key,status,proposal_count,seed,solve_time_seconds,created_by_user_id) VALUES (60,6,60,'request-a','QUEUED',2,42,10,90)");
            SQLException duplicateRequest=assertThrows(SQLException.class,()->statement.execute("INSERT INTO timetable_generation_jobs(organization_id,academic_year_id,request_key,status,proposal_count,seed,solve_time_seconds,created_by_user_id) VALUES (6,60,'request-a','QUEUED',2,42,10,90)"));
            assertEquals("23505",duplicateRequest.getSQLState());
            SQLException concurrent=assertThrows(SQLException.class,()->statement.execute("INSERT INTO timetable_generation_jobs(organization_id,academic_year_id,request_key,status,proposal_count,seed,solve_time_seconds,created_by_user_id) VALUES (6,60,'request-b','RUNNING',2,43,10,90)"));
            assertEquals("23505",concurrent.getSQLState());
            SQLException crossTenant=assertThrows(SQLException.class,()->statement.execute("INSERT INTO timetable_generation_jobs(organization_id,academic_year_id,request_key,status,proposal_count,seed,solve_time_seconds,created_by_user_id) VALUES (7,60,'request-c','QUEUED',1,44,10,90)"));
            assertEquals("23503",crossTenant.getSQLState());
            assertEquals(1,statement.executeUpdate("UPDATE timetable_generation_jobs SET cancel_requested=true,status='CANCEL_REQUESTED',version=version+1 WHERE id=60 AND organization_id=6 AND version=0"));
            assertEquals(0,statement.executeUpdate("UPDATE timetable_generation_jobs SET status='CANCELLED' WHERE id=60 AND organization_id=7"));
        }
    }

    @Test
    void publicationRangesAreSchoolWideAndSnapshotsAreImmutable() throws Exception {
        try(Connection connection=connection();Statement statement=connection.createStatement()){
            statement.execute("INSERT INTO academic_years(id,organization_id,name,start_date,end_date) VALUES (70,7,'2026','2026-01-01','2026-12-31')");
            statement.execute("INSERT INTO timetable_drafts(id,organization_id,academic_year_id,name,workflow_status,created_by_user_id,updated_by_user_id) VALUES (70,7,70,'Approved','APPROVED',90,90),(71,7,70,'Future','APPROVED',90,90)");
            statement.execute("INSERT INTO timetable_publications(id,organization_id,academic_year_id,source_draft_id,publication_number,effective_from,effective_to_exclusive,published_by_user_id,school_name) VALUES (70,7,70,70,1,'2026-01-01','2026-07-01',90,'Original School')");
            SQLException overlap=assertThrows(SQLException.class,()->statement.execute("INSERT INTO timetable_publications(organization_id,academic_year_id,source_draft_id,publication_number,effective_from,effective_to_exclusive,published_by_user_id,school_name) VALUES (7,70,71,2,'2026-06-30',NULL,90,'Original School')"));
            assertEquals("23P01",overlap.getSQLState());
            assertDoesNotThrow(()->statement.execute("INSERT INTO timetable_publications(id,organization_id,academic_year_id,source_draft_id,publication_number,effective_from,effective_to_exclusive,published_by_user_id,school_name) VALUES (71,7,70,71,2,'2026-07-01',NULL,90,'Original School')"));
            statement.execute("INSERT INTO timetable_publication_entries(organization_id,academic_year_id,publication_id,grade_level_id,grade_name,section_id,section_name,wing_id,wing_name,bell_schedule_interval_id,day_of_week,start_time,end_time,interval_type,activity_name) VALUES (7,70,70,1,'Grade 1',1,'A',1,'Junior',1,1,'09:00','09:30','RECESS','Recess')");
            SQLException immutable=assertThrows(SQLException.class,()->statement.execute("UPDATE timetable_publication_entries SET activity_name='Changed' WHERE publication_id=70"));
            assertEquals("55000",immutable.getSQLState());
            SQLException tenant=assertThrows(SQLException.class,()->statement.execute("INSERT INTO timetable_publication_entries(organization_id,academic_year_id,publication_id,grade_level_id,grade_name,section_id,section_name,wing_id,wing_name,bell_schedule_interval_id,day_of_week,start_time,end_time,interval_type,activity_name) VALUES (8,70,70,1,'Grade 1',1,'A',1,'Junior',2,1,'10:00','10:30','RECESS','Recess')"));
            assertEquals("23503",tenant.getSQLState());
        }
    }

    @Test
    void concurrentPublicationRaceAllowsOnlyOneEffectiveRange() throws Exception {
        try(Connection setup=connection();Statement statement=setup.createStatement()){
            statement.execute("INSERT INTO academic_years(id,organization_id,name,start_date,end_date) VALUES (80,8,'2026','2026-01-01','2026-12-31')");
            statement.execute("INSERT INTO timetable_drafts(id,organization_id,academic_year_id,name,workflow_status,created_by_user_id,updated_by_user_id) VALUES (80,8,80,'Candidate A','APPROVED',90,90),(81,8,80,'Candidate B','APPROVED',90,90)");
        }
        try(Connection first=connection();Connection second=connection()){
            first.setAutoCommit(false);second.setAutoCommit(false);
            first.createStatement().execute("INSERT INTO timetable_publications(organization_id,academic_year_id,source_draft_id,publication_number,effective_from,published_by_user_id,school_name) VALUES (8,80,80,1,'2026-01-01',90,'Race School')");
            CompletableFuture<String> competing=CompletableFuture.supplyAsync(()->{
                try{second.createStatement().execute("INSERT INTO timetable_publications(organization_id,academic_year_id,source_draft_id,publication_number,effective_from,published_by_user_id,school_name) VALUES (8,80,81,2,'2026-01-01',91,'Race School')");second.commit();return "inserted";}
                catch(SQLException exception){try{second.rollback();}catch(SQLException ignored){}return exception.getSQLState();}
            });
            first.commit();
            assertEquals("23P01",competing.get(5,TimeUnit.SECONDS));
        }
    }

    @Test
    void draftServiceKeepsPreviewNonMutatingAllowsIncompleteAndRejectsStaleVersion() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO academic_years(id,organization_id,name,start_date,end_date) VALUES (50,5,'2026','2026-01-01','2026-12-31')");
            statement.execute("INSERT INTO grade_levels(id,organization_id,academic_year_id,code,name,display_order) VALUES (50,5,50,'G1','Grade 1',1)");
            statement.execute("INSERT INTO sections(id,organization_id,academic_year_id,grade_level_id,code,name,display_order) VALUES (50,5,50,50,'A','Section A',1)");
            statement.execute("INSERT INTO subjects(id,organization_id,academic_year_id,code,name) VALUES (50,5,50,'MAT','Mathematics')");
            statement.execute("INSERT INTO grade_level_subjects(id,organization_id,academic_year_id,grade_level_id,subject_id,display_order) VALUES (50,5,50,50,50,1)");
            statement.execute("INSERT INTO timetable_wings(id,organization_id,code,name,display_order) VALUES (50,5,'JR','Junior',1)");
            statement.execute("INSERT INTO timetable_grade_wings(organization_id,academic_year_id,grade_level_id,wing_id) VALUES (5,50,50,50)");
            statement.execute("INSERT INTO timetable_rooms(id,organization_id,wing_id,code,name,exclusive_use) VALUES (50,5,50,'G1A','Grade 1 A',true)");
            statement.execute("INSERT INTO timetable_section_home_rooms(organization_id,academic_year_id,section_id,room_id) VALUES (5,50,50,50)");
            statement.execute("INSERT INTO timetable_bell_schedules(id,organization_id,name) VALUES (50,5,'Standard')");
            statement.execute("INSERT INTO timetable_bell_schedule_days(id,organization_id,bell_schedule_id,day_of_week,working_day,day_start_time,day_end_time) VALUES (50,5,50,1,true,'09:00','12:00')");
            statement.execute("INSERT INTO timetable_bell_intervals(id,organization_id,bell_schedule_day_id,interval_type,name,start_time,end_time,display_order,coverage_mode,assignee_requirement,schedulable) VALUES (50,5,50,'TEACHING','Period 1','09:00','09:40',1,'SECTION','QUALIFIED_SUBJECT_TEACHER',true),(51,5,50,'TEACHING','Period 2','09:40','10:20',2,'SECTION','QUALIFIED_SUBJECT_TEACHER',true)");
            statement.execute("INSERT INTO timetable_bell_schedule_assignments(organization_id,academic_year_id,wing_id,bell_schedule_id) VALUES (5,50,50,50)");
            statement.execute("INSERT INTO timetable_section_subject_requirements(organization_id,academic_year_id,grade_level_id,section_id,grade_level_subject_id,periods_per_week,maximum_periods_per_day,required_each_working_day,double_period_blocks_per_week) VALUES (5,50,50,50,50,2,2,true,1)");
            statement.execute("INSERT INTO teacher_assignments(organization_id,academic_year_id,grade_level_id,section_id,grade_level_subject_id,teacher_user_id,assignment_type) VALUES (5,50,50,50,50,500,'SUBJECT_TEACHER')");
            statement.execute("INSERT INTO timetable_teacher_policies(organization_id,academic_year_id,teacher_user_id,maximum_periods_per_day,maximum_periods_per_week,maximum_consecutive_periods,minimum_rest_minutes) VALUES (5,50,500,2,2,2,0)");
        }
        DriverManagerDataSource dataSource=new DriverManagerDataSource(jdbcUrl,"postgres","");
        JdbcClient client=JdbcClient.create(dataSource);
        TimetableConfigurationService configuration=new TimetableConfigurationService(client,mock(IdentityTeacherEligibilityClient.class),org->java.time.ZoneId.of("Asia/Kolkata"));
        JdbcTransactionManager transactionManager=new JdbcTransactionManager(dataSource);
        TimetableDraftService service=new TimetableDraftService(client,configuration,transactionManager);
        TransactionTemplate transactions=new TransactionTemplate(transactionManager);
        var draft=transactions.execute(status->service.create(5,90,new CreateDraftRequest(50,"Manual draft")));
        LessonRequest lesson=new LessonRequest(50,50,ActivityType.TEACHING,"Mathematics",50L,null,null,0);
        long before=client.sql("SELECT COUNT(*) FROM timetable_draft_section_slots WHERE organization_id=5").query(Long.class).single();
        var preview=transactions.execute(status->service.preview(5,draft.id(),null,lesson));
        assertTrue(preview.canApply());
        assertEquals(before,client.sql("SELECT COUNT(*) FROM timetable_draft_section_slots WHERE organization_id=5").query(Long.class).single());
        var slot=transactions.execute(status->service.add(5,90,draft.id(),lesson));
        assertEquals(null,slot.teacherUserId());
        var validation=transactions.execute(status->service.validate(5,draft.id()));
        assertTrue(validation.editValid());
        assertTrue(!validation.complete());
        assertTrue(validation.issues().stream().anyMatch(issue -> issue.code().equals("DOUBLE_PERIOD_REQUIREMENT_INCOMPLETE")));
        assertThrows(TimetableConfigurationConflictException.class,
                ()->transactions.execute(status->service.add(5,90,draft.id(),lesson)));
        LessonRequest adjacent=new LessonRequest(50,51,ActivityType.TEACHING,"Mathematics",50L,null,null,1);
        var secondSlot=transactions.execute(status->service.add(5,90,draft.id(),adjacent));
        var doubleComplete=transactions.execute(status->service.validate(5,draft.id()));
        assertTrue(doubleComplete.issues().stream().noneMatch(issue -> issue.code().startsWith("DOUBLE_PERIOD")));
        var swapPreview=service.previewSwap(5,draft.id(),slot.id(),
                new SwapLessonsRequest(secondSlot.id(),2,slot.version(),secondSlot.version()));
        assertTrue(swapPreview.canApply());
        assertEquals(2,service.get(5,draft.id()).version());
        assertEquals(50,service.get(5,draft.id()).slots().stream().filter(value->value.id()==slot.id()).findFirst().orElseThrow().bellScheduleIntervalId());

        TimetableGenerationService generation=new TimetableGenerationService(client,configuration,service,transactionManager,Runnable::run,new ObjectMapper());
        transactions.execute(status->generation.submit(5,90,new GenerateRequest(50,2,123,1,null,false,"generation-a")));
        var completed=generation.get(5,client.sql("SELECT id FROM timetable_generation_jobs WHERE organization_id=5 AND request_key='generation-a'").query(Long.class).single());
        assertEquals(JobStatus.COMPLETED,completed.status());
        assertEquals(2,completed.proposals().size());
        assertTrue(completed.proposals().stream().allMatch(proposal->proposal.feasible()&&proposal.draftId()!=null));
        assertEquals(2,completed.proposals().stream().map(proposal->proposal.draftId()).distinct().count());
        long sourceDraftId=completed.proposals().getFirst().draftId();
        var sourceDraft=service.get(5,sourceDraftId);var sourceSlot=sourceDraft.slots().getFirst();
        transactions.execute(status->service.pin(5,90,sourceDraftId,sourceSlot.id(),new com.dawnrise.academic.timetable.dto.TimetableDraftDtos.VersionRequest(sourceDraft.version(),sourceSlot.version()),true));
        long pinnedSourceVersion=service.get(5,sourceDraftId).version();
        transactions.execute(status->generation.submit(5,90,new GenerateRequest(50,1,222,1,sourceDraftId,false,"generation-pinned")));
        var pinnedJob=generation.get(5,client.sql("SELECT id FROM timetable_generation_jobs WHERE organization_id=5 AND request_key='generation-pinned'").query(Long.class).single());
        var pinnedDraft=service.get(5,pinnedJob.proposals().getFirst().draftId());
        assertTrue(pinnedDraft.slots().stream().anyMatch(value->value.pinned()&&value.bellScheduleIntervalId()==sourceSlot.bellScheduleIntervalId()&&value.teacherUserId().equals(sourceSlot.teacherUserId())));
        assertEquals(pinnedSourceVersion,service.get(5,sourceDraftId).version());

        IdentityTeacherEligibilityClient publicationTeachers=mock(IdentityTeacherEligibilityClient.class);
        org.mockito.Mockito.when(publicationTeachers.checkBatch(org.mockito.ArgumentMatchers.eq(5L),org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation->new BatchTeachingEligibilityResponse(invocation.<List<Long>>getArgument(1).stream()
                        .map(id->new TeachingEligibilityResponse(id,5L,"Original Teacher "+id,true,TeachingEligibilityReason.ELIGIBLE)).toList()));
        AtomicReference<String> schoolName=new AtomicReference<>("Original School Name");
        SchoolTimeZoneClient publicationSchool=new SchoolTimeZoneClient(){
            @Override public java.time.ZoneId getTimeZone(long organizationId){return java.time.ZoneId.of("Asia/Kolkata");}
            @Override public String getSchoolName(long organizationId){return schoolName.get();}
        };
        TimetableWorkflowService workflow=new TimetableWorkflowService(client,service,configuration,
                publicationSchool,mock(IdentityGuardianAccessClient.class),publicationTeachers);
        var submitted=transactions.execute(status->workflow.submit(5,91,pinnedDraft.id(),pinnedDraft.version()));
        assertEquals(com.dawnrise.academic.timetable.dto.TimetableDraftDtos.WorkflowStatus.IN_REVIEW,submitted.status());
        assertThrows(TimetableConfigurationConflictException.class,()->transactions.execute(status->service.add(5,90,pinnedDraft.id(),adjacent)));
        var approved=transactions.execute(status->workflow.approve(5,92,pinnedDraft.id(),submitted.version()));
        LocalDate publicationDate=LocalDate.now(publicationSchool.getTimeZone(5));
        var published=transactions.execute(status->workflow.publish(5,93,pinnedDraft.id(),approved.version(),publicationDate,null));
        assertEquals(com.dawnrise.academic.timetable.dto.TimetableDraftDtos.WorkflowStatus.PUBLISHED,published.status());
        assertTrue(published.publicationId()!=null);
        var schoolView=workflow.school(5,50,publicationDate);
        assertEquals(published.publicationId(),schoolView.publicationId());
        assertEquals("Asia/Kolkata",schoolView.timeZoneId());
        assertEquals("Original School Name",schoolView.schoolName());
        assertTrue(schoolView.entries().stream().anyMatch(entry->entry.subjectName().equals("Mathematics")));
        assertTrue(schoolView.entries().stream().filter(entry->entry.teacherUserId()!=null)
                .allMatch(entry->entry.teacherDisplayName().equals("Original Teacher "+entry.teacherUserId())));
        org.mockito.Mockito.when(publicationTeachers.checkBatch(org.mockito.ArgumentMatchers.eq(5L),org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation->new BatchTeachingEligibilityResponse(invocation.<List<Long>>getArgument(1).stream()
                        .map(id->new TeachingEligibilityResponse(id,5L,"Renamed Teacher",true,TeachingEligibilityReason.ELIGIBLE)).toList()));
        schoolName.set("Renamed School");
        var historical=workflow.school(5,50,publicationDate);
        assertEquals("Original School Name",historical.schoolName());
        assertTrue(historical.entries().stream().filter(entry->entry.teacherUserId()!=null)
                .allMatch(entry->entry.teacherDisplayName().startsWith("Original Teacher")));

        var reviewDraft=service.get(5,completed.proposals().getLast().draftId());
        var reviewSubmitted=transactions.execute(status->workflow.submit(5,91,reviewDraft.id(),reviewDraft.version()));
        var returned=transactions.execute(status->workflow.returnDraft(5,92,reviewDraft.id(),reviewSubmitted.version(),"Teacher allocation must change"));
        assertEquals(com.dawnrise.academic.timetable.dto.TimetableDraftDtos.WorkflowStatus.DRAFT,returned.status());
        assertEquals("Teacher allocation must change",returned.history().getLast().reason());

        client.sql("DELETE FROM teacher_assignments WHERE organization_id=5 AND academic_year_id=50 AND teacher_user_id=500").update();
        transactions.execute(status->generation.submit(5,90,new GenerateRequest(50,1,321,1,null,false,"generation-impossible")));
        var impossible=generation.get(5,client.sql("SELECT id FROM timetable_generation_jobs WHERE organization_id=5 AND request_key='generation-impossible'").query(Long.class).single());
        assertEquals(JobStatus.COMPLETED,impossible.status());
        assertTrue(!impossible.proposals().getFirst().feasible());
        assertEquals(null,impossible.proposals().getFirst().draftId());
        assertTrue(!impossible.proposals().getFirst().unmetRequirements().isEmpty());

        AtomicReference<Runnable> queued=new AtomicReference<>();
        TimetableGenerationService cancellable=new TimetableGenerationService(client,configuration,service,transactionManager,queued::set,new ObjectMapper());
        var queuedJob=transactions.execute(status->cancellable.submit(5,90,new GenerateRequest(50,1,456,1,null,false,"generation-cancel")));
        var cancelled=transactions.execute(status->cancellable.cancel(5,queuedJob.id(),queuedJob.version()));
        assertEquals(JobStatus.CANCELLED,cancelled.status());
        queued.get().run();
        assertEquals(JobStatus.CANCELLED,cancellable.get(5,queuedJob.id()).status());
        assertThrows(com.dawnrise.academic.timetable.exception.TimetableConfigurationNotFoundException.class,()->cancellable.get(6,queuedJob.id()));

        var active=transactions.execute(status->cancellable.submit(5,90,new GenerateRequest(50,1,789,1,null,false,"generation-active")));
        assertEquals(JobStatus.QUEUED,active.status());
        assertThrows(TimetableConfigurationConflictException.class,()->transactions.execute(status->cancellable.submit(5,90,new GenerateRequest(50,1,790,1,null,false,"generation-concurrent"))));
    }

    @Test
    void dailyCoverKeepsOriginalAssignmentAndProjectsOnlyApprovedProposal() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO academic_years(id,organization_id,name,start_date,end_date,status) VALUES (90,9,'2026','2026-01-01','2026-12-31','ACTIVE')");
            statement.execute("INSERT INTO grade_levels(id,organization_id,academic_year_id,code,name,display_order) VALUES (90,9,90,'G1','Grade 1',1)");
            statement.execute("INSERT INTO sections(id,organization_id,academic_year_id,grade_level_id,code,name,display_order) VALUES (90,9,90,90,'A','Section A',1),(91,9,90,90,'B','Section B',2)");
            statement.execute("INSERT INTO subjects(id,organization_id,academic_year_id,code,name) VALUES (90,9,90,'ENG','English')");
            statement.execute("INSERT INTO grade_level_subjects(id,organization_id,academic_year_id,grade_level_id,subject_id,display_order) VALUES (90,9,90,90,90,1)");
            statement.execute("INSERT INTO timetable_wings(id,organization_id,code,name,display_order) VALUES (90,9,'JR','Junior',1)");
            statement.execute("INSERT INTO timetable_grade_wings(organization_id,academic_year_id,grade_level_id,wing_id) VALUES (9,90,90,90)");
            statement.execute("INSERT INTO teacher_assignments(organization_id,academic_year_id,grade_level_id,section_id,grade_level_subject_id,teacher_user_id,assignment_type) VALUES (9,90,90,90,90,901,'SUBJECT_TEACHER'),(9,90,90,91,90,902,'SUBJECT_TEACHER')");
            statement.execute("INSERT INTO student_enrollments(organization_id,academic_year_id,grade_level_id,section_id,student_user_id,roll_number,enrolled_on) VALUES (9,90,90,90,903,'1','2026-01-01')");
            statement.execute("INSERT INTO timetable_teacher_policies(organization_id,academic_year_id,teacher_user_id,maximum_periods_per_day,maximum_periods_per_week,maximum_consecutive_periods) VALUES (9,90,902,1,1,1)");
            statement.execute("INSERT INTO timetable_drafts(id,organization_id,academic_year_id,name,created_by_user_id,updated_by_user_id) VALUES (90,9,90,'Published source',900,900)");
            statement.execute("INSERT INTO timetable_publications(id,organization_id,academic_year_id,source_draft_id,publication_number,effective_from,published_by_user_id,school_name) VALUES (90,9,90,90,1,'2026-01-01',900,'Coverage School')");
            statement.execute("""
                    INSERT INTO timetable_publication_entries
                    (id,organization_id,academic_year_id,publication_id,grade_level_id,grade_name,section_id,section_name,
                     wing_id,wing_name,bell_schedule_interval_id,day_of_week,start_time,end_time,interval_type,
                     activity_name,grade_level_subject_id,subject_name,teacher_user_id,teacher_display_name)
                    VALUES (90,9,90,90,90,'Grade 1',90,'Section A',90,'Junior',900,
                    """ + date.getDayOfWeek().getValue() + ",'09:00','09:40','TEACHING','English',90,'English',901,'Absent Teacher')");
        }
        DriverManagerDataSource source = new DriverManagerDataSource(jdbcUrl, "postgres", "");
        JdbcClient client = JdbcClient.create(source);
        IdentityTeacherEligibilityClient identity = mock(IdentityTeacherEligibilityClient.class);
        AtomicBoolean coverEligible = new AtomicBoolean(true);
        org.mockito.Mockito.when(identity.check(9, 901)).thenReturn(
                new TeachingEligibilityResponse(901L, 9L, "Absent Teacher", true, TeachingEligibilityReason.ELIGIBLE));
        org.mockito.Mockito.when(identity.checkBatch(org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> new BatchTeachingEligibilityResponse(invocation.<List<Long>>getArgument(1).stream()
                        .map(id -> new TeachingEligibilityResponse(id, 9L, id == 902 ? "Cover Teacher" : "Absent Teacher",
                                id != 902 || coverEligible.get(), id != 902 || coverEligible.get()
                                ? TeachingEligibilityReason.ELIGIBLE : TeachingEligibilityReason.USER_NOT_ACTIVE)).toList()));
        IdentityGuardianAccessClient guardians = mock(IdentityGuardianAccessClient.class);
        org.mockito.Mockito.when(guardians.hasActiveRelationship(9, 904, 903)).thenReturn(true);
        DailyCoverageService coverage = new DailyCoverageService(client, org -> ZoneId.of("Asia/Kolkata"), identity, guardians);
        var recorded = coverage.recordAbsence(9, 900,
                new RecordAbsenceRequest(901, date, AbsenceType.KNOWN_LEAVE, null, null, List.of(),
                        "Approved leave", "absence-90"));
        assertEquals(recorded, coverage.recordAbsence(9, 900,
                new RecordAbsenceRequest(901, date, AbsenceType.KNOWN_LEAVE, null, null, List.of(),
                        "Approved leave", "absence-90")));
        var affected = coverage.cases(9, 90, date, null, null).getFirst();
        var candidate = coverage.candidates(9, affected.id()).stream()
                .filter(item -> item.candidateKey().equals("TEACHER:902")).findFirst().orElseThrow();
        assertTrue(candidate.eligible());
        assertTrue(candidate.recommended());
        var proposal = coverage.manualProposal(9, 900, affected.id(),
                new CandidateChoiceRequest(candidate.candidateKey(), affected.version(), null, "proposal-90"));
        assertEquals("PENDING_APPROVAL", proposal.status());
        var beforeApproval = coverage.daily(9, 90, date, null, null, 90L, null).entries().getFirst();
        assertEquals("Absent Teacher", beforeApproval.actualAssignment().teacherDisplayName());
        assertTrue(beforeApproval.coverage() == null);
        coverEligible.set(false);
        assertThrows(TimetableConfigurationConflictException.class,
                () -> coverage.approve(9, 901, proposal.id(), new VersionRequest(proposal.version(), "approval-ineligible-90")));
        coverEligible.set(true);
        client.sql("""
                INSERT INTO timetable_daily_cover_incidents
                    (organization_id,case_id,status,first_alert_at,next_escalation_at,escalation_level)
                VALUES(9,:case,'OPEN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1)
                """).param("case", affected.id()).update();
        coverage.approve(9, 901, proposal.id(), new VersionRequest(proposal.version(), "approval-90"));
        var approved = coverage.daily(9, 90, date, null, null, 90L, null).entries().getFirst();
        assertEquals("Absent Teacher", approved.originalAssignment().teacherDisplayName());
        assertEquals("Cover Teacher", approved.actualAssignment().teacherDisplayName());
        assertEquals("APPROVED", approved.coverage().status());
        assertEquals("RESOLVED", client.sql("SELECT status FROM timetable_daily_cover_incidents "
                        + "WHERE organization_id=9 AND case_id=:case")
                .param("case", affected.id()).query(String.class).single());
        assertEquals(1, client.sql("SELECT COUNT(*) FROM timetable_daily_cover_notification_outbox "
                        + "WHERE organization_id=9 AND case_id=:case AND event_type='CONFIRMED_SUBSTITUTION'")
                .param("case", affected.id()).query(Integer.class).single());
        assertEquals("Cover Teacher", coverage.daily(9, 90, date, null, null, null, 902L)
                .entries().getFirst().actualAssignment().teacherDisplayName());
        assertEquals("Cover Teacher", coverage.student(9, 903, 90, date)
                .entries().getFirst().actualAssignment().teacherDisplayName());
        assertEquals("Cover Teacher", coverage.parent(9, 904, 903, 90, date)
                .entries().getFirst().actualAssignment().teacherDisplayName());
        org.mockito.Mockito.verify(guardians).hasActiveRelationship(9, 904, 903);
        assertThrows(com.dawnrise.academic.timetable.exception.TimetableConfigurationNotFoundException.class,
                () -> coverage.parent(9, 905, 903, 90, date));
    }

    private void seedAcademicStructure(Statement statement) throws SQLException {
        statement.execute("INSERT INTO academic_years(id,organization_id,name,start_date,end_date) VALUES (1,1,'2026','2026-01-01','2026-12-31')");
        statement.execute("INSERT INTO grade_levels(id,organization_id,academic_year_id,code,name,display_order) VALUES (1,1,1,'G1','Grade 1',1)");
        statement.execute("INSERT INTO sections(id,organization_id,academic_year_id,grade_level_id,code,name,display_order) VALUES (1,1,1,1,'A','Section A',1)");
        statement.execute("INSERT INTO subjects(id,organization_id,academic_year_id,code,name) VALUES (1,1,1,'MAT','Mathematics')");
        statement.execute("INSERT INTO grade_level_subjects(id,organization_id,academic_year_id,grade_level_id,subject_id,display_order) VALUES (1,1,1,1,1,1)");
    }

    private String intervalSql(long id, String name, String start, String end, int order) {
        return "INSERT INTO timetable_bell_intervals " +
                "(id,organization_id,bell_schedule_day_id,interval_type,name,start_time,end_time,display_order,coverage_mode,assignee_requirement,schedulable) VALUES (" +
                id + ",1,1,'TEACHING','" + name + "','" + start + "','" + end + "'," + order +
                ",'SECTION','QUALIFIED_SUBJECT_TEACHER',true)";
    }

    private String draftSlotSql(long id, long section, long teacher, Integer room) {
        return "INSERT INTO timetable_draft_section_slots " +
                "(id,organization_id,academic_year_id,draft_id,grade_level_id,section_id,wing_id,bell_schedule_interval_id," +
                "day_of_week,start_time,end_time,activity_type,activity_name,grade_level_subject_id,teacher_user_id,room_id,room_exclusive) VALUES (" +
                id + ",3,30,30,30," + section + ",30,30,1,'09:00','09:40','TEACHING','Mathematics',30," + teacher + "," +
                (room == null ? "NULL,false" : room + ",true") + ")";
    }

    private Connection connection() throws SQLException { return DriverManager.getConnection(jdbcUrl, "postgres", ""); }

    private Path executable(String name) throws Exception {
        Process process = new ProcessBuilder("/usr/bin/env", "sh", "-c", "command -v " + name).start();
        String result = new String(process.getInputStream().readAllBytes()).trim();
        return process.waitFor() == 0 && !result.isBlank() ? Path.of(result) : null;
    }

    private void run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        int exit = process.waitFor();
        assertEquals(0, exit, () -> String.join(" ", command) + " failed:\n" + output);
    }
}
