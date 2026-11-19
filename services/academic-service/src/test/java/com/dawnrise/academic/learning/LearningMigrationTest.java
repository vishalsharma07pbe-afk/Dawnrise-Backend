package com.dawnrise.academic.learning;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import java.io.IOException;import java.net.ServerSocket;import java.nio.file.*;import java.sql.DriverManager;import java.util.Comparator;
import static org.junit.jupiter.api.Assertions.*;import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LearningMigrationTest {
 private Path root,data,socket,pgCtl;private String url;
 @BeforeAll void start()throws Exception{Path init=bin("initdb");pgCtl=bin("pg_ctl");assumeTrue(init!=null&&pgCtl!=null,"PostgreSQL 16 binaries are required");root=Files.createTempDirectory("dawnrise-learning-v40-");data=root.resolve("data");run(init.toString(),"-D",data.toString(),"-A","trust","-U","postgres","--no-locale","--encoding=UTF8");int port;try(ServerSocket s=new ServerSocket(0)){port=s.getLocalPort();}socket=Files.createDirectory(Path.of("/tmp","dr-learning-"+port));run(pgCtl.toString(),"-D",data.toString(),"-l",root.resolve("postgres.log").toString(),"-o","-p "+port+" -k "+socket+" -F","-w","start");url="jdbc:postgresql://127.0.0.1:"+port+"/postgres";}
 @AfterAll void stop()throws Exception{if(pgCtl!=null&&data!=null&&Files.exists(data.resolve("postmaster.pid")))run(pgCtl.toString(),"-D",data.toString(),"-m","immediate","-w","stop");if(root!=null&&Files.exists(root))try(var paths=Files.walk(root)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}if(socket!=null)Files.deleteIfExists(socket);}
 @Test void migratesFromV39AndCreatesLearningAndPointsContracts()throws Exception{Flyway.configure().dataSource(url,"postgres","").locations("filesystem:src/main/resources/db/migration").target("39").load().migrate();Flyway.configure().dataSource(url,"postgres","").locations("filesystem:src/main/resources/db/migration").load().migrate();try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){try(var v=s.executeQuery("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")){assertTrue(v.next());assertEquals("41",v.getString(1));}for(String table:java.util.List.of("learning_posts","learning_tasks","learning_student_progress","learning_reviews","student_points_ledger","learning_reward_settings","learning_notification_outbox"))try(var found=s.executeQuery("SELECT to_regclass('public."+table+"') IS NOT NULL")){assertTrue(found.next());assertTrue(found.getBoolean(1),table);}}}
 private Path bin(String name)throws Exception{var p=new ProcessBuilder("/usr/bin/env","sh","-c","command -v "+name).start();String result=new String(p.getInputStream().readAllBytes()).trim();return p.waitFor()==0&&!result.isBlank()?Path.of(result):null;}
 private void run(String...cmd)throws Exception{var p=new ProcessBuilder(cmd).redirectErrorStream(true).start();String out=new String(p.getInputStream().readAllBytes());assertEquals(0,p.waitFor(),()->String.join(" ",cmd)+" failed:\n"+out);}
}
