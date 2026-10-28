package com.dawnrise.academic.timetable.service;

import com.dawnrise.academic.common.integration.school.SchoolTimeZoneClient;
import com.dawnrise.academic.results.integration.identity.IdentityGuardianAccessClient;
import com.dawnrise.academic.teacherassignment.integration.identity.BatchTeachingEligibilityResponse;
import com.dawnrise.academic.teacherassignment.integration.identity.IdentityTeacherEligibilityClient;
import com.dawnrise.academic.teacherassignment.integration.identity.TeachingEligibilityResponse;
import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.BellDayResponse;
import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.BellIntervalResponse;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.SlotResponse;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.ValidationReport;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.WorkflowStatus;
import com.dawnrise.academic.timetable.dto.TimetableWorkflowDtos.PublishedEntry;
import com.dawnrise.academic.timetable.dto.TimetableWorkflowDtos.PublishedTimetable;
import com.dawnrise.academic.timetable.dto.TimetableWorkflowDtos.SchoolCalendarDate;
import com.dawnrise.academic.timetable.dto.TimetableWorkflowDtos.WorkflowEvent;
import com.dawnrise.academic.timetable.dto.TimetableWorkflowDtos.WorkflowResponse;
import com.dawnrise.academic.timetable.exception.InvalidTimetableConfigurationException;
import com.dawnrise.academic.timetable.exception.TimetableConfigurationConflictException;
import com.dawnrise.academic.timetable.exception.TimetableConfigurationNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class TimetableWorkflowService {
    private static final int IDENTITY_BATCH_LIMIT=100;
    private final JdbcClient jdbc;
    private final TimetableDraftService drafts;
    private final TimetableConfigurationService configuration;
    private final SchoolTimeZoneClient timeZones;
    private final IdentityGuardianAccessClient guardians;
    private final IdentityTeacherEligibilityClient teachers;

    public TimetableWorkflowService(JdbcClient jdbc, TimetableDraftService drafts,
                                    TimetableConfigurationService configuration,
                                    SchoolTimeZoneClient timeZones,
                                    IdentityGuardianAccessClient guardians,
                                    IdentityTeacherEligibilityClient teachers) {
        this.jdbc=jdbc;this.drafts=drafts;this.configuration=configuration;
        this.timeZones=timeZones;this.guardians=guardians;
        this.teachers=teachers;
    }

    public SchoolCalendarDate currentSchoolDate(long org){
        var zone=timeZones.getTimeZone(org);
        return new SchoolCalendarDate(LocalDate.now(zone),zone.getId());
    }

    @Transactional
    public WorkflowResponse submit(long org,long actor,long draftId,long expectedVersion){
        Head h=lock(org,draftId);requireVersion(h,expectedVersion);requireStatus(h,WorkflowStatus.DRAFT);
        requireComplete(org,draftId);return transition(org,actor,h,WorkflowStatus.IN_REVIEW,null);
    }

    @Transactional
    public WorkflowResponse returnDraft(long org,long actor,long draftId,long expectedVersion,String reason){
        Head h=lock(org,draftId);requireVersion(h,expectedVersion);
        if(h.status()!=WorkflowStatus.IN_REVIEW&&h.status()!=WorkflowStatus.APPROVED)
            throw conflict("Only an in-review or approved timetable can be returned");
        String normalized=reason==null?"":reason.trim();
        if(normalized.isEmpty())throw new InvalidTimetableConfigurationException("A return reason is required");
        return transition(org,actor,h,WorkflowStatus.DRAFT,normalized);
    }

    @Transactional
    public WorkflowResponse approve(long org,long actor,long draftId,long expectedVersion){
        Head h=lock(org,draftId);requireVersion(h,expectedVersion);requireStatus(h,WorkflowStatus.IN_REVIEW);
        requireComplete(org,draftId);return transition(org,actor,h,WorkflowStatus.APPROVED,null);
    }

    @Transactional
    public WorkflowResponse publish(long org,long actor,long draftId,long expectedVersion,
                                    LocalDate from,LocalDate toExclusive){
        Head h=lock(org,draftId);requireVersion(h,expectedVersion);requireStatus(h,WorkflowStatus.APPROVED);
        requireComplete(org,draftId);
        YearRange year=year(org,h.year());
        if(from==null)throw new InvalidTimetableConfigurationException("effectiveFrom is required");
        LocalDate today=LocalDate.now(timeZones.getTimeZone(org));
        if(from.isBefore(today))
            throw new InvalidTimetableConfigurationException("Timetable validity cannot start before today");
        if(from.isBefore(year.start())||from.isAfter(year.end())||toExclusive!=null&&
                (!toExclusive.isAfter(from)||toExclusive.isAfter(year.end().plusDays(1))))
            throw new InvalidTimetableConfigurationException("Effective range must be inside the academic year and use an exclusive end date");
        jdbc.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(:org,:year)) locked")
                .param("org",Math.toIntExact(org)).param("year",Math.toIntExact(h.year())).query(Integer.class).single();
        List<PublicationRange> existing=jdbc.sql("SELECT id,effective_from,effective_to_exclusive FROM timetable_publications WHERE organization_id=:org AND academic_year_id=:year ORDER BY effective_from FOR UPDATE")
                .param("org",org).param("year",h.year()).query((rs,n)->new PublicationRange(rs.getLong(1),rs.getObject(2,LocalDate.class),rs.getObject(3,LocalDate.class))).list();
        for(PublicationRange range:existing){
            if(range.from().isBefore(from)&&range.to()==null)
                jdbc.sql("UPDATE timetable_publications SET effective_to_exclusive=:from WHERE id=:id AND effective_to_exclusive IS NULL")
                        .param("from",from).param("id",range.id()).update();
        }
        long number=jdbc.sql("SELECT COALESCE(MAX(publication_number),0)+1 FROM timetable_publications WHERE organization_id=:org AND academic_year_id=:year")
                .param("org",org).param("year",h.year()).query(Long.class).single();
        long publicationId;
        String schoolName=timeZones.getSchoolName(org);
        Map<Long,String> teacherNames=teacherNames(org,draftId);
        try {
            publicationId=jdbc.sql("INSERT INTO timetable_publications(organization_id,academic_year_id,source_draft_id,publication_number,effective_from,effective_to_exclusive,published_by_user_id,school_name) VALUES(:org,:year,:draft,:number,:from,:to,:actor,:schoolName) RETURNING id")
                    .param("org",org).param("year",h.year()).param("draft",draftId).param("number",number)
                    .param("from",from).param("to",toExclusive).param("actor",actor).param("schoolName",schoolName).query(Long.class).single();
            snapshot(org,h.year(),draftId,publicationId,teacherNames);
        } catch(DataIntegrityViolationException ex){throw conflict("The effective range overlaps another publication; reload and retry");}
        jdbc.sql("""
                UPDATE timetable_drafts SET effective_from_intent=:from,
                    effective_to_exclusive_intent=:to WHERE id=:draft AND organization_id=:org
                """).param("from",from).param("to",toExclusive).param("draft",draftId).param("org",org).update();
        WorkflowResponse response=transition(org,actor,h,WorkflowStatus.PUBLISHED,null);
        return new WorkflowResponse(response.draftId(),response.academicYearId(),response.status(),response.version(),publicationId,response.history());
    }

    @Transactional(readOnly=true)
    public PublishedTimetable school(long org,long year,LocalDate date){return published(org,year,date,null,null,null,null);}
    @Transactional(readOnly=true)
    public PublishedTimetable wing(long org,long year,long wing,LocalDate date){return published(org,year,date,wing,null,null,null);}
    @Transactional(readOnly=true)
    public PublishedTimetable grade(long org,long year,long grade,LocalDate date){return published(org,year,date,null,grade,null,null);}
    @Transactional(readOnly=true)
    public PublishedTimetable section(long org,long year,long section,LocalDate date){return published(org,year,date,null,null,section,null);}
    @Transactional(readOnly=true)
    public PublishedTimetable teacher(long org,long year,long teacher,LocalDate date){
        PublishedTimetable school=published(org,year,date,null,null,null,null);
        Map<String,List<PublishedEntry>> cells=school.entries().stream().collect(java.util.stream.Collectors.groupingBy(
                entry->entry.dayOfWeek()+":"+entry.startTime()+":"+entry.endTime(),LinkedHashMap::new,java.util.stream.Collectors.toList()));
        List<PublishedEntry> entries=new ArrayList<>();
        for(List<PublishedEntry> cell:cells.values()){
            List<PublishedEntry> assigned=cell.stream().filter(entry->Objects.equals(entry.teacherUserId(),teacher)).toList();
            if(!assigned.isEmpty()){entries.addAll(assigned);continue;}
            PublishedEntry representative=cell.get(0);
            if(representative.intervalType()!=com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.IntervalType.TEACHING){
                entries.add(representative);continue;
            }
            entries.add(new PublishedEntry(representative.gradeLevelId(),representative.gradeName(),representative.sectionId(),
                    representative.sectionName(),representative.wingId(),representative.wingName(),representative.bellScheduleIntervalId(),
                    representative.dayOfWeek(),representative.startTime(),representative.endTime(),representative.intervalType(),
                    "Free period",null,null,null,null,null,null));
        }
        return new PublishedTimetable(school.publicationId(),school.academicYearId(),school.schoolName(),school.academicYearName(),
                school.effectiveFrom(),school.effectiveToExclusive(),school.publishedAt(),school.generatedAt(),school.timeZoneId(),List.copyOf(entries));
    }
    @Transactional(readOnly=true)
    public PublishedTimetable student(long org,long student,long year,LocalDate date){
        long section=jdbc.sql("SELECT section_id FROM student_enrollments WHERE organization_id=:org AND academic_year_id=:year AND student_user_id=:student AND enrolled_on<=:date AND (ended_on IS NULL OR ended_on>=:date) ORDER BY id DESC LIMIT 1")
                .param("org",org).param("year",year).param("student",student).param("date",resolveDate(org,date)).query(Long.class).optional()
                .orElseThrow(()->new TimetableConfigurationNotFoundException("Active student enrollment not found"));
        return section(org,year,section,date);
    }
    @Transactional(readOnly=true)
    public PublishedTimetable parent(long org,long guardian,long student,long year,LocalDate date){
        if(!guardians.hasActiveRelationship(org,guardian,student))throw new TimetableConfigurationNotFoundException("Published timetable not found");
        return student(org,student,year,date);
    }

    private void requireComplete(long org,long draft){ValidationReport report=drafts.validate(org,draft);
        if(!report.editValid()||!report.complete())throw conflict("Timetable is not complete and conflict-free: "+
                report.issues().stream().findFirst().map(i->i.code()+" - "+i.message()).orElse("validation failed"));}
    private WorkflowResponse transition(long org,long actor,Head h,WorkflowStatus to,String reason){
        int changed=jdbc.sql("UPDATE timetable_drafts SET workflow_status=:to,version=version+1,updated_by_user_id=:actor,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND organization_id=:org AND version=:version")
                .param("to",to.name()).param("actor",actor).param("id",h.id()).param("org",org).param("version",h.version()).update();
        if(changed!=1)throw conflict("Draft was modified by another request; reload and retry");
        jdbc.sql("INSERT INTO timetable_workflow_events(organization_id,academic_year_id,draft_id,from_status,to_status,reason,actor_user_id) VALUES(:org,:year,:draft,:from,:to,:reason,:actor)")
                .param("org",org).param("year",h.year()).param("draft",h.id()).param("from",h.status().name()).param("to",to.name()).param("reason",reason).param("actor",actor).update();
        jdbc.sql("INSERT INTO timetable_draft_audit(organization_id,academic_year_id,draft_id,actor_user_id,action,details) VALUES(:org,:year,:draft,:actor,:action,jsonb_build_object('reason',CAST(:reason AS TEXT)))")
                .param("org",org).param("year",h.year()).param("draft",h.id()).param("actor",actor)
                .param("action",switch(to){case DRAFT->"RETURN";case IN_REVIEW->"SUBMIT";case APPROVED->"APPROVE";case PUBLISHED->"PUBLISH";})
                .param("reason",reason).update();
        return response(org,h.id(),h.year(),to,h.version()+1,null);
    }
    private WorkflowResponse response(long org,long draft,long year,WorkflowStatus status,long version,Long publication){return new WorkflowResponse(draft,year,status,version,publication,
            jdbc.sql("SELECT id,from_status,to_status,reason,actor_user_id,occurred_at FROM timetable_workflow_events WHERE organization_id=:org AND academic_year_id=:year AND draft_id=:draft ORDER BY occurred_at,id")
                    .param("org",org).param("year",year).param("draft",draft).query((rs,n)->new WorkflowEvent(rs.getLong(1),WorkflowStatus.valueOf(rs.getString(2)),WorkflowStatus.valueOf(rs.getString(3)),rs.getString(4),rs.getLong(5),rs.getObject(6,OffsetDateTime.class))).list());}

    private void snapshot(long org,long year,long draft,long publication,Map<Long,String> teacherNames){
        Map<String,SlotResponse> slots=new HashMap<>();
        for(SlotResponse s:drafts.get(org,draft).slots())slots.put(s.sectionId()+":"+s.dayOfWeek()+":"+s.bellScheduleIntervalId(),s);
        List<SectionInfo> sections=jdbc.sql("SELECT s.id,s.grade_level_id,s.name,g.name FROM sections s JOIN grade_levels g ON g.id=s.grade_level_id AND g.organization_id=s.organization_id WHERE s.organization_id=:org AND s.academic_year_id=:year ORDER BY g.display_order,s.display_order")
                .param("org",org).param("year",year).query((rs,n)->new SectionInfo(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4))).list();
        for(SectionInfo section:sections){var resolved=configuration.resolvedSchedule(org,year,section.id());
            String wingName=jdbc.sql("SELECT name FROM timetable_wings WHERE id=:id AND organization_id=:org").param("id",resolved.wingId()).param("org",org).query(String.class).single();
            for(BellDayResponse day:resolved.bellSchedule().days())for(BellIntervalResponse interval:day.intervals()){
                SlotResponse slot=slots.get(section.id()+":"+day.dayOfWeek()+":"+interval.id());
                String subjectName=slot==null||slot.gradeLevelSubjectId()==null?null:jdbc.sql("SELECT s.name FROM grade_level_subjects gs JOIN subjects s ON s.id=gs.subject_id AND s.organization_id=gs.organization_id WHERE gs.id=:id AND gs.organization_id=:org").param("id",slot.gradeLevelSubjectId()).param("org",org).query(String.class).optional().orElse(null);
                String roomName=slot==null||slot.roomId()==null?null:jdbc.sql("SELECT name FROM timetable_rooms WHERE id=:id AND organization_id=:org").param("id",slot.roomId()).param("org",org).query(String.class).optional().orElse(null);
                Long teacher=slot==null?null:slot.teacherUserId();
                jdbc.sql("INSERT INTO timetable_publication_entries(organization_id,academic_year_id,publication_id,grade_level_id,grade_name,section_id,section_name,wing_id,wing_name,bell_schedule_interval_id,day_of_week,start_time,end_time,interval_type,activity_name,grade_level_subject_id,subject_name,teacher_user_id,teacher_display_name,room_id,room_name) VALUES(:org,:year,:publication,:grade,:gradeName,:section,:sectionName,:wing,:wingName,:interval,:day,:start,:end,:type,:activity,:subject,:subjectName,:teacher,:teacherName,:room,:roomName)")
                        .param("org",org).param("year",year).param("publication",publication).param("grade",section.grade()).param("gradeName",section.gradeName()).param("section",section.id()).param("sectionName",section.name()).param("wing",resolved.wingId()).param("wingName",wingName).param("interval",interval.id()).param("day",day.dayOfWeek()).param("start",interval.startTime()).param("end",interval.endTime()).param("type",interval.intervalType().name()).param("activity",slot==null?interval.name():slot.activityName()).param("subject",slot==null?null:slot.gradeLevelSubjectId()).param("subjectName",subjectName).param("teacher",teacher).param("teacherName",teacher==null?null:teacherNames.get(teacher)).param("room",slot==null?null:slot.roomId()).param("roomName",roomName).update();
            }}
    }
    private PublishedTimetable published(long org,long year,LocalDate requested,Long wing,Long grade,Long section,Long teacher){
        LocalDate date=resolveDate(org,requested);Publication p=jdbc.sql("SELECT p.id,p.effective_from,p.effective_to_exclusive,p.published_at,y.name,p.school_name FROM timetable_publications p JOIN academic_years y ON y.id=p.academic_year_id AND y.organization_id=p.organization_id WHERE p.organization_id=:org AND p.academic_year_id=:year AND p.effective_from<=:date AND (p.effective_to_exclusive IS NULL OR p.effective_to_exclusive>:date) ORDER BY p.effective_from DESC LIMIT 1")
                .param("org",org).param("year",year).param("date",date).query((rs,n)->new Publication(rs.getLong(1),rs.getObject(2,LocalDate.class),rs.getObject(3,LocalDate.class),rs.getObject(4,OffsetDateTime.class),rs.getString(5),rs.getString(6))).optional().orElseThrow(()->new TimetableConfigurationNotFoundException("Published timetable not found for the requested date"));
        List<PublishedEntry> entries=jdbc.sql("SELECT grade_level_id,grade_name,section_id,section_name,wing_id,wing_name,bell_schedule_interval_id,day_of_week,start_time,end_time,interval_type,activity_name,grade_level_subject_id,subject_name,teacher_user_id,teacher_display_name,room_id,room_name FROM timetable_publication_entries WHERE organization_id=:org AND academic_year_id=:year AND publication_id=:publication AND (CAST(:wing AS BIGINT) IS NULL OR wing_id=:wing) AND (CAST(:grade AS BIGINT) IS NULL OR grade_level_id=:grade) AND (CAST(:section AS BIGINT) IS NULL OR section_id=:section) AND (CAST(:teacher AS BIGINT) IS NULL OR teacher_user_id=:teacher) ORDER BY day_of_week,start_time,grade_level_id,section_id")
                .param("org",org).param("year",year).param("publication",p.id()).param("wing",wing).param("grade",grade).param("section",section).param("teacher",teacher)
                .query((rs,n)->new PublishedEntry(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getString(4),rs.getLong(5),rs.getString(6),rs.getLong(7),rs.getInt(8),rs.getObject(9,java.time.LocalTime.class),rs.getObject(10,java.time.LocalTime.class),com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.IntervalType.valueOf(rs.getString(11)),rs.getString(12),(Long)rs.getObject(13),rs.getString(14),(Long)rs.getObject(15),rs.getString(16),(Long)rs.getObject(17),rs.getString(18))).list();
        var zone=timeZones.getTimeZone(org);return new PublishedTimetable(p.id(),year,p.schoolName(),p.yearName(),p.from(),p.to(),p.publishedAt(),OffsetDateTime.now(zone),zone.getId(),entries);
    }
    private LocalDate resolveDate(long org,LocalDate date){return date==null?LocalDate.now(timeZones.getTimeZone(org)):date;}
    private Head lock(long org,long draft){return jdbc.sql("SELECT id,academic_year_id,workflow_status,version FROM timetable_drafts WHERE id=:id AND organization_id=:org FOR UPDATE").param("id",draft).param("org",org).query((rs,n)->new Head(rs.getLong(1),rs.getLong(2),WorkflowStatus.valueOf(rs.getString(3)),rs.getLong(4))).optional().orElseThrow(()->new TimetableConfigurationNotFoundException("Timetable draft not found"));}
    private YearRange year(long org,long year){return jdbc.sql("SELECT start_date,end_date FROM academic_years WHERE id=:year AND organization_id=:org").param("year",year).param("org",org).query((rs,n)->new YearRange(rs.getObject(1,LocalDate.class),rs.getObject(2,LocalDate.class))).single();}
    private void requireVersion(Head h,long expected){if(h.version()!=expected)throw conflict("Draft was modified by another request; reload and retry");}
    private void requireStatus(Head h,WorkflowStatus status){if(h.status()!=status)throw conflict("Timetable must be "+status+" for this transition");}
    private TimetableConfigurationConflictException conflict(String message){return new TimetableConfigurationConflictException(message);}
    private record Head(long id,long year,WorkflowStatus status,long version){}
    private record YearRange(LocalDate start,LocalDate end){}
    private record PublicationRange(long id,LocalDate from,LocalDate to){}
    private Map<Long,String> teacherNames(long org,long draft){
        List<Long> ids=jdbc.sql("SELECT DISTINCT teacher_user_id FROM timetable_draft_section_slots WHERE organization_id=:org AND draft_id=:draft AND teacher_user_id IS NOT NULL ORDER BY teacher_user_id")
                .param("org",org).param("draft",draft).query(Long.class).list();
        return resolveTeacherDisplayNames(org,ids);
    }
    Map<Long,String> resolveTeacherDisplayNames(long org,List<Long> ids){
        Map<Long,String> names=new HashMap<>();
        for(int start=0;start<ids.size();start+=IDENTITY_BATCH_LIMIT){
            List<Long> requested=ids.subList(start,Math.min(start+IDENTITY_BATCH_LIMIT,ids.size()));
            BatchTeachingEligibilityResponse response=teachers.checkBatch(org,requested);
            if(response==null||response.results()==null)throw new InvalidTimetableConfigurationException("Assigned teacher identities could not be resolved");
            Set<Long> batchIds=new LinkedHashSet<>(requested);
            for(TeachingEligibilityResponse item:response.results()){
                if(item==null||item.userId()==null||!batchIds.contains(item.userId())||!Objects.equals(item.organizationId(),org)||item.displayName()==null||item.displayName().isBlank())
                    throw new InvalidTimetableConfigurationException("An assigned teacher could not be resolved for this organization");
                if(names.putIfAbsent(item.userId(),item.displayName().trim())!=null)
                    throw new InvalidTimetableConfigurationException("Identity-service returned a duplicate assigned teacher");
            }
            if(!names.keySet().containsAll(requested))throw new InvalidTimetableConfigurationException("An assigned teacher could not be resolved for this organization");
        }
        return Map.copyOf(names);
    }
    private record Publication(long id,LocalDate from,LocalDate to,OffsetDateTime publishedAt,String yearName,String schoolName){}
    private record SectionInfo(long id,long grade,String name,String gradeName){}
}
