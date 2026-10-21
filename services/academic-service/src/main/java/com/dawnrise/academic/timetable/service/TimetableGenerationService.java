package com.dawnrise.academic.timetable.service;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.BellDayResponse;
import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.BellIntervalResponse;
import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.IntervalType;
import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.ResolvedSectionSchedule;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.ActivityType;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.Conflict;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.CreateDraftRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.DraftResponse;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.LessonRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.SlotResponse;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.ValidationReport;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.VersionRequest;
import com.dawnrise.academic.timetable.dto.TimetableGenerationDtos.GenerateRequest;
import com.dawnrise.academic.timetable.dto.TimetableGenerationDtos.JobResponse;
import com.dawnrise.academic.timetable.dto.TimetableGenerationDtos.JobStatus;
import com.dawnrise.academic.timetable.dto.TimetableGenerationDtos.ProposalResponse;
import com.dawnrise.academic.timetable.dto.TimetableGenerationDtos.RetryRequest;
import com.dawnrise.academic.timetable.exception.InvalidTimetableConfigurationException;
import com.dawnrise.academic.timetable.exception.TimetableConfigurationConflictException;
import com.dawnrise.academic.timetable.exception.TimetableConfigurationNotFoundException;
import com.dawnrise.academic.timetable.generation.TimetablePlanningModel.Lesson;
import com.dawnrise.academic.timetable.generation.TimetablePlanningModel.Schedule;
import com.dawnrise.academic.timetable.generation.TimetablePlanningModel.SlotOption;
import com.dawnrise.academic.timetable.generation.TimetablePlanningModel.TeacherPolicy;
import com.dawnrise.academic.timetable.generation.TimetableScoreCalculator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class TimetableGenerationService {
    private final JdbcClient jdbc;
    private final TimetableConfigurationService configurationService;
    private final TimetableDraftService draftService;
    private final PlatformTransactionManager transactionManager;
    private final Executor executor;
    private final ObjectMapper objectMapper;
    private final Map<Long,Solver<Schedule>> runningSolvers=new ConcurrentHashMap<>();

    public TimetableGenerationService(JdbcClient jdbc, TimetableConfigurationService configurationService,
                                      TimetableDraftService draftService, PlatformTransactionManager transactionManager,
                                      @Qualifier("timetableGenerationExecutor") Executor executor,ObjectMapper objectMapper){
        this.jdbc=jdbc;this.configurationService=configurationService;this.draftService=draftService;
        this.transactionManager=transactionManager;this.executor=executor;this.objectMapper=objectMapper;
    }

    @Transactional
    public JobResponse submit(long org,long actor,GenerateRequest request){
        requireYear(org,request.academicYearId());
        if(request.sourceDraftId()!=null){DraftResponse source=draftService.get(org,request.sourceDraftId());if(source.academicYearId()!=request.academicYearId())throw new InvalidTimetableConfigurationException("Pinned source draft belongs to another academic year");}
        Long existing=jdbc.sql("SELECT id FROM timetable_generation_jobs WHERE organization_id=:org AND academic_year_id=:year AND request_key=:key")
                .param("org",org).param("year",request.academicYearId()).param("key",request.requestKey().trim()).query(Long.class).optional().orElse(null);
        if(existing!=null)return get(org,existing);
        long id;
        try{id=jdbc.sql("""
                INSERT INTO timetable_generation_jobs(organization_id,academic_year_id,request_key,status,proposal_count,seed,
                solve_time_seconds,source_draft_id,prefer_class_teacher_first,created_by_user_id)
                VALUES(:org,:year,:key,'QUEUED',:count,:seed,:seconds,:source,:prefer,:actor) RETURNING id
                """).param("org",org).param("year",request.academicYearId()).param("key",request.requestKey().trim())
                .param("count",request.proposalCount()).param("seed",request.seed()).param("seconds",request.solveTimeSeconds())
                .param("source",request.sourceDraftId()).param("prefer",request.preferClassTeacherFirst()).param("actor",actor).query(Long.class).single();}
        catch(DataIntegrityViolationException exception){throw new TimetableConfigurationConflictException("Another timetable generation job is already active for this academic year");}
        long jobId=id;TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){try{executor.execute(()->run(jobId));}catch(RuntimeException exception){finish(jobId,JobStatus.FAILED,"Generation queue is full; retry with a new request key");}}});
        return get(org,id);
    }

    @Transactional(readOnly=true)
    public JobResponse get(long org,long id){
        JobRow row=job(org,id);List<ProposalResponse> proposals=jdbc.sql("SELECT * FROM timetable_generation_proposals WHERE organization_id=:org AND academic_year_id=:year AND job_id=:job ORDER BY proposal_index")
                .param("org",org).param("year",row.year()).param("job",id).query((rs,n)->new ProposalResponse(rs.getInt("proposal_index"),(Long)rs.getObject("draft_id"),rs.getBoolean("feasible"),rs.getLong("hard_score"),rs.getLong("soft_score"),jsonLongMap(rs.getString("score_breakdown")),jsonConflicts(rs.getString("validation_findings")),jsonStrings(rs.getString("unmet_requirements")))).list();
        return new JobResponse(row.id(),row.year(),row.key(),row.status(),row.count(),row.completed(),row.seed(),row.seconds(),row.source(),row.prefer(),row.cancel(),row.failure(),row.created(),row.started(),row.finished(),row.version(),proposals);
    }

    @Transactional
    public JobResponse cancel(long org,long id,long expectedVersion){
        JobRow row=jobForUpdate(org,id);if(row.version()!=expectedVersion)throw new TimetableConfigurationConflictException("Generation job was modified; reload and retry");
        if(row.status()==JobStatus.COMPLETED||row.status()==JobStatus.FAILED||row.status()==JobStatus.CANCELLED)return get(org,id);
        JobStatus target=row.status()==JobStatus.QUEUED?JobStatus.CANCELLED:JobStatus.CANCEL_REQUESTED;
        int changed=jdbc.sql("UPDATE timetable_generation_jobs SET cancel_requested=true,status=:status,finished_at=CASE WHEN :status='CANCELLED' THEN CURRENT_TIMESTAMP ELSE finished_at END,version=version+1 WHERE id=:id AND organization_id=:org AND version=:version")
                .param("status",target.name()).param("id",id).param("org",org).param("version",expectedVersion).update();if(changed!=1)throw new TimetableConfigurationConflictException("Generation job was modified; reload and retry");
        Solver<Schedule> solver=runningSolvers.get(id);if(solver!=null)solver.terminateEarly();return get(org,id);
    }

    @Transactional
    public JobResponse retry(long org,long actor,long id,RetryRequest request){
        JobRow row=jobForUpdate(org,id);if(row.version()!=request.expectedVersion())throw new TimetableConfigurationConflictException("Generation job was modified; reload and retry");
        if(row.status()!=JobStatus.FAILED&&row.status()!=JobStatus.CANCELLED&&row.status()!=JobStatus.COMPLETED)throw new TimetableConfigurationConflictException("Only a finished generation job can be retried");
        return submit(org,actor,new GenerateRequest(row.year(),row.count(),row.seed(),row.seconds(),row.source(),row.prefer(),request.requestKey()));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs(){
        transaction().executeWithoutResult(status->jdbc.sql("""
                UPDATE timetable_generation_jobs SET status='FAILED',failure_message='Generation was interrupted by a service restart; retry with a new request key',
                finished_at=CURRENT_TIMESTAMP,version=version+1 WHERE status IN ('QUEUED','RUNNING','CANCEL_REQUESTED')
                """).update());
    }

    private void run(long jobId){
        JobRow initial=transaction().execute(status->claim(jobId));if(initial==null)return;
        try{
            BuildResult build=transaction().execute(status->buildProblem(initial));
            if(!build.unmet().isEmpty()){for(int index=1;index<=initial.count();index++)persistProposal(initial,index,null,null,HardSoftScore.of(-build.unmet().size(),0),List.of(),build.unmet());finish(initial.id(),JobStatus.COMPLETED,null);return;}
            for(int index=1;index<=initial.count();index++){
                if(cancelled(initial.id())){finish(initial.id(),JobStatus.CANCELLED,null);return;}
                Schedule problem=copy(build.schedule());Solver<Schedule> solver=solver(initial.seed()+index-1,Duration.ofSeconds(Math.max(1,initial.seconds()/initial.count())));runningSolvers.put(initial.id(),solver);
                Schedule solved=solver.solve(problem);runningSolvers.remove(initial.id());HardSoftScore score=solved.getScore();
                if(cancelled(initial.id())){finish(initial.id(),JobStatus.CANCELLED,null);return;}
                saveProposal(initial,index,solved,score);progress(initial.id(),index);
            }
            finish(initial.id(),JobStatus.COMPLETED,null);
        }catch(Exception exception){runningSolvers.remove(jobId);finish(jobId,JobStatus.FAILED,safeMessage(exception));}
    }

    private Solver<Schedule> solver(long seed,Duration limit){
        SolverConfig config=new SolverConfig().withSolutionClass(Schedule.class).withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig().withEasyScoreCalculatorClass(TimetableScoreCalculator.class))
                .withTerminationSpentLimit(limit).withRandomSeed(seed).withEnvironmentMode(EnvironmentMode.NO_ASSERT);
        return SolverFactory.<Schedule>create(config).buildSolver();
    }

    private BuildResult buildProblem(JobRow job){
        List<Requirement> requirements=jdbc.sql("""
                SELECT r.section_id,r.grade_level_id,r.grade_level_subject_id,r.periods_per_week,
                r.required_each_working_day,r.maximum_periods_per_day,r.double_period_blocks_per_week,s.name
                FROM timetable_section_subject_requirements r JOIN grade_level_subjects gs
                ON gs.id=r.grade_level_subject_id AND gs.organization_id=r.organization_id AND gs.academic_year_id=r.academic_year_id
                JOIN subjects s ON s.id=gs.subject_id AND s.organization_id=gs.organization_id AND s.academic_year_id=gs.academic_year_id
                WHERE r.organization_id=:org AND r.academic_year_id=:year ORDER BY r.section_id,r.grade_level_subject_id
                """).param("org",job.org()).param("year",job.year()).query((rs,n)->new Requirement(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getInt(4),rs.getBoolean(5),rs.getInt(6),rs.getInt(7),rs.getString(8))).list();
        List<String> unmet=new ArrayList<>();List<Lesson> lessons=new ArrayList<>();long lessonId=1;
        Map<Long,List<SlotResponse>> pins=new HashMap<>();
        if(job.source()!=null)for(SlotResponse slot:draftService.get(job.org(),job.source()).slots())if(slot.pinned())pins.computeIfAbsent(slot.sectionId(),key->new ArrayList<>()).add(slot);
        Map<Long,Integer> requiredBySection=new LinkedHashMap<>();requirements.forEach(r->requiredBySection.merge(r.section(),r.weekly(),Integer::sum));
        List<Long> sections=jdbc.sql("SELECT id FROM sections WHERE organization_id=:org AND academic_year_id=:year ORDER BY id").param("org",job.org()).param("year",job.year()).query(Long.class).list();
        for(Long section:sections){
            ResolvedSectionSchedule resolved=configurationService.resolvedSchedule(job.org(),job.year(),section);
            int capacity=resolved.bellSchedule().days().stream().flatMap(day->day.intervals().stream()).mapToInt(interval->interval.intervalType()==IntervalType.TEACHING?1:0).sum();
            int required=requiredBySection.getOrDefault(section,0);
            if(capacity!=required)unmet.add("Section "+section+" has "+capacity+" teaching slots but subject requirements total "+required);
        }
        Set<Long> consumedPins=new LinkedHashSet<>();
        for(Requirement requirement:requirements){
            List<SlotResponse> subjectPins=pins.getOrDefault(requirement.section(),List.of()).stream().filter(s->requirement.subject()==(s.gradeLevelSubjectId()==null?-1:s.gradeLevelSubjectId())).toList();
            if(subjectPins.size()>requirement.weekly()){unmet.add("Section "+requirement.section()+" has more pinned "+requirement.name()+" lessons than required");continue;}
            Long classTeacher=classTeacher(job.org(),job.year(),requirement.section());List<SlotOption> options=options(job,requirement);
            if(options.isEmpty()){unmet.add("No eligible time, teacher, and room combination for "+requirement.name()+" in section "+requirement.section());continue;}
            for(SlotResponse pin:subjectPins){consumedPins.add(pin.id());SlotOption fixed=pinOption(pin,options);if(fixed==null){unmet.add("Pinned lesson "+pin.id()+" is no longer eligible");continue;}lessons.add(new Lesson(lessonId++,requirement.section(),requirement.grade(),requirement.subject(),false,requirement.name(),requirement.daily(),requirement.doubles(),requirement.requiredDaily(),classTeacher,true,List.of(fixed),fixed));}
            for(int occurrence=subjectPins.size();occurrence<requirement.weekly();occurrence++)lessons.add(new Lesson(lessonId++,requirement.section(),requirement.grade(),requirement.subject(),false,requirement.name(),requirement.daily(),requirement.doubles(),requirement.requiredDaily(),classTeacher,false,options,null));
        }
        for(var entry:pins.entrySet())for(SlotResponse pin:entry.getValue())if(!consumedPins.contains(pin.id())){
            if(pin.activityType()!=ActivityType.SUPERVISED_ACTIVITY){unmet.add("Pinned slot "+pin.id()+" does not match a configured teaching requirement");continue;}
            boolean exclusive=pin.roomId()!=null&&jdbc.sql("SELECT exclusive_use FROM timetable_rooms WHERE id=:id AND organization_id=:org").param("id",pin.roomId()).param("org",job.org()).query(Boolean.class).optional().orElse(false);
            SlotOption fixed=new SlotOption(pin.bellScheduleIntervalId(),pin.dayOfWeek(),pin.startTime(),pin.endTime(),pin.wingId(),pin.teacherUserId(),pin.roomId(),exclusive,false,false,false);
            lessons.add(new Lesson(lessonId++,pin.sectionId(),pin.gradeLevelId(),0,true,pin.activityName(),Integer.MAX_VALUE,0,false,null,true,List.of(fixed),fixed));
            consumedPins.add(pin.id());
        }
        Map<Long,TeacherPolicy> policies=new HashMap<>();jdbc.sql("SELECT teacher_user_id,maximum_periods_per_day,maximum_periods_per_week,maximum_consecutive_periods,minimum_rest_minutes FROM timetable_teacher_policies WHERE organization_id=:org AND academic_year_id=:year")
                .param("org",job.org()).param("year",job.year()).query((rs,n)->{policies.put(rs.getLong(1),new TeacherPolicy(rs.getInt(2),rs.getInt(3),rs.getInt(4),rs.getInt(5)));return 0;}).list();
        return new BuildResult(new Schedule(lessons,Map.copyOf(policies),job.prefer()),List.copyOf(unmet));
    }

    private List<SlotOption> options(JobRow job,Requirement requirement){
        ResolvedSectionSchedule schedule=configurationService.resolvedSchedule(job.org(),job.year(),requirement.section());
        List<Long> teachers=jdbc.sql("""
                SELECT teacher_user_id FROM teacher_assignments WHERE organization_id=:org AND academic_year_id=:year
                AND section_id=:section AND grade_level_subject_id=:subject AND assignment_type='SUBJECT_TEACHER'
                UNION SELECT teacher_user_id FROM timetable_alternate_teacher_eligibilities WHERE organization_id=:org
                AND academic_year_id=:year AND grade_level_subject_id=:subject AND active
                AND (section_id IS NULL OR section_id=:section) AND (wing_id IS NULL OR wing_id=:wing)
                AND (valid_from IS NULL OR valid_from<=CURRENT_DATE) AND (valid_to IS NULL OR valid_to>=CURRENT_DATE)
                ORDER BY teacher_user_id
                """).param("org",job.org()).param("year",job.year()).param("section",requirement.section()).param("subject",requirement.subject()).param("wing",schedule.wingId()).query(Long.class).list();
        List<RoomChoice> rooms=rooms(job.org(),job.year(),requirement.subject());if(rooms.isEmpty())return List.of();
        List<SlotOption> result=new ArrayList<>();
        for(BellDayResponse day:schedule.bellSchedule().days()){
            List<BellIntervalResponse> teaching=day.intervals().stream().filter(i->i.intervalType()==IntervalType.TEACHING).toList();
            for(int position=0;position<teaching.size();position++){BellIntervalResponse interval=teaching.get(position);
                for(Long teacher:teachers)if(teacherAvailable(job.org(),job.year(),teacher,day.dayOfWeek(),interval.startTime(),interval.endTime()))
                    for(RoomChoice room:rooms)result.add(new SlotOption(interval.id(),day.dayOfWeek(),interval.startTime(),interval.endTime(),schedule.wingId(),teacher,room.id(),room.exclusive(),preferred(job.org(),job.year(),teacher,day.dayOfWeek(),interval.startTime(),interval.endTime()),discouraged(job.org(),job.year(),teacher,day.dayOfWeek(),interval.startTime(),interval.endTime()),position==0));
            }
        }
        return List.copyOf(result);
    }

    private List<RoomChoice> rooms(long org,long year,long subject){
        long required=jdbc.sql("SELECT COUNT(*) FROM timetable_subject_room_requirements WHERE organization_id=:org AND academic_year_id=:year AND grade_level_subject_id=:subject AND required")
                .param("org",org).param("year",year).param("subject",subject).query(Long.class).single();
        if(required==0)return List.of(new RoomChoice(null,false));
        return jdbc.sql("""
                SELECT r.id,r.exclusive_use FROM timetable_rooms r WHERE r.organization_id=:org AND r.active
                AND NOT EXISTS(SELECT 1 FROM timetable_subject_room_requirements rr WHERE rr.organization_id=:org
                AND rr.academic_year_id=:year AND rr.grade_level_subject_id=:subject AND rr.required
                AND NOT EXISTS(SELECT 1 FROM timetable_room_capabilities rc WHERE rc.organization_id=r.organization_id
                AND rc.room_id=r.id AND rc.capability_code=rr.capability_code)) ORDER BY r.code
                """).param("org",org).param("year",year).param("subject",subject).query((rs,n)->new RoomChoice(rs.getLong(1),rs.getBoolean(2))).list();
    }

    private boolean teacherAvailable(long org,long year,long teacher,int day,LocalTime start,LocalTime end){
        long unavailable=availabilityCount(org,year,teacher,day,start,end,"UNAVAILABLE",false);if(unavailable>0)return false;
        long rules=jdbc.sql("SELECT COUNT(*) FROM timetable_teacher_availability WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher AND day_of_week=:day AND availability_type='AVAILABLE'")
                .param("org",org).param("year",year).param("teacher",teacher).param("day",day).query(Long.class).single();
        return rules==0||availabilityCount(org,year,teacher,day,start,end,"AVAILABLE",true)>0;
    }
    private boolean preferred(long org,long year,long teacher,int day,LocalTime start,LocalTime end){return availabilityCount(org,year,teacher,day,start,end,"PREFERRED",false)>0;}
    private boolean discouraged(long org,long year,long teacher,int day,LocalTime start,LocalTime end){return availabilityCount(org,year,teacher,day,start,end,"DISCOURAGED",false)>0;}
    private long availabilityCount(long org,long year,long teacher,int day,LocalTime start,LocalTime end,String type,boolean contains){String time=contains?"start_time<=:start AND end_time>=:end":"start_time<:end AND end_time>:start";return jdbc.sql("SELECT COUNT(*) FROM timetable_teacher_availability WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher AND day_of_week=:day AND availability_type=:type AND "+time).param("org",org).param("year",year).param("teacher",teacher).param("day",day).param("type",type).param("start",start).param("end",end).query(Long.class).single();}
    private Long classTeacher(long org,long year,long section){return jdbc.sql("SELECT teacher_user_id FROM teacher_assignments WHERE organization_id=:org AND academic_year_id=:year AND section_id=:section AND assignment_type='CLASS_TEACHER'").param("org",org).param("year",year).param("section",section).query(Long.class).optional().orElse(null);}
    private SlotOption pinOption(SlotResponse pin,List<SlotOption> options){return options.stream().filter(o->o.intervalId()==pin.bellScheduleIntervalId()&&java.util.Objects.equals(o.teacherUserId(),pin.teacherUserId())&&java.util.Objects.equals(o.roomId(),pin.roomId())).findFirst().orElse(null);}

    private void saveProposal(JobRow job,int index,Schedule solved,HardSoftScore score){
        if(score==null||!score.isFeasible()){persistProposal(job,index,null,solved,score==null?HardSoftScore.of(-1,0):score,List.of(),List.of("Solver did not find a fully assigned hard-constraint-feasible timetable within the limit"));return;}
        SaveResult result=new TransactionTemplate(transactionManager).execute(status->{
            DraftResponse draft=draftService.create(job.org(),job.actor(),new CreateDraftRequest(job.year(),"Generated "+job.id()+" proposal "+index));long version=draft.version();
            for(Lesson lesson:solved.getLessons()){SlotOption option=lesson.getOption();if(option==null){status.setRollbackOnly();return new SaveResult(null,List.of(),List.of("Solver returned an unassigned teaching requirement"));}
                SlotResponse slot=draftService.add(job.org(),job.actor(),draft.id(),new LessonRequest(lesson.getSectionId(),option.intervalId(),lesson.isSupervisedActivity()?ActivityType.SUPERVISED_ACTIVITY:ActivityType.TEACHING,lesson.getActivityName(),lesson.isSupervisedActivity()?null:lesson.getSubjectId(),option.teacherUserId(),option.roomId(),version++));
                if(lesson.isPinned()){draftService.pin(job.org(),job.actor(),draft.id(),slot.id(),new VersionRequest(version,slot.version()),true);version++;}
            }
            ValidationReport validation=draftService.validate(job.org(),draft.id());if(!validation.editValid()||!validation.complete()){status.setRollbackOnly();return new SaveResult(null,validation.issues(),validation.issues().stream().map(Conflict::message).toList());}
            return new SaveResult(draft.id(),validation.issues(),List.of());});
        persistProposal(job,index,result.draft(),solved,score,result.findings(),result.unmet());
    }

    private void persistProposal(JobRow job,int index,Long draft,Schedule schedule,HardSoftScore score,List<Conflict> findings,List<String> unmet){
        long hard=score==null?-1:score.hardScore(),soft=score==null?0:score.softScore();boolean feasible=draft!=null&&hard>=0&&unmet.isEmpty();
        Map<String,Long> breakdown=scoreBreakdown(score,schedule);
        transaction().executeWithoutResult(status->jdbc.sql("""
                INSERT INTO timetable_generation_proposals(organization_id,academic_year_id,job_id,proposal_index,draft_id,
                feasible,hard_score,soft_score,score_breakdown,validation_findings,unmet_requirements)
                VALUES(:org,:year,:job,:idx,:draft,:feasible,:hard,:soft,CAST(:scores AS jsonb),CAST(:findings AS jsonb),CAST(:unmet AS jsonb))
                """).param("org",job.org()).param("year",job.year()).param("job",job.id()).param("idx",index).param("draft",draft)
                .param("feasible",feasible).param("hard",hard).param("soft",soft).param("scores",json(breakdown))
                .param("findings",json(findings)).param("unmet",json(unmet)).update());
    }

    private JobRow claim(long id){return jdbc.sql("UPDATE timetable_generation_jobs SET status='RUNNING',started_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=:id AND status='QUEUED' RETURNING *").param("id",id).query((rs,n)->mapJob(rs)).optional().orElse(null);}
    private boolean cancelled(long id){return transaction().execute(status->jdbc.sql("SELECT cancel_requested FROM timetable_generation_jobs WHERE id=:id").param("id",id).query(Boolean.class).single());}
    private void progress(long id,int completed){transaction().executeWithoutResult(status->jdbc.sql("UPDATE timetable_generation_jobs SET completed_proposals=:done,version=version+1 WHERE id=:id").param("done",completed).param("id",id).update());}
    private void finish(long id,JobStatus status,String failure){transaction().executeWithoutResult(tx->jdbc.sql("UPDATE timetable_generation_jobs SET status=:status,failure_message=:failure,finished_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=:id").param("status",status.name()).param("failure",failure).param("id",id).update());}
    private JobRow job(long org,long id){return jdbc.sql("SELECT * FROM timetable_generation_jobs WHERE id=:id AND organization_id=:org").param("id",id).param("org",org).query((rs,n)->mapJob(rs)).optional().orElseThrow(()->new TimetableConfigurationNotFoundException("Timetable generation job not found"));}
    private JobRow jobForUpdate(long org,long id){return jdbc.sql("SELECT * FROM timetable_generation_jobs WHERE id=:id AND organization_id=:org FOR UPDATE").param("id",id).param("org",org).query((rs,n)->mapJob(rs)).optional().orElseThrow(()->new TimetableConfigurationNotFoundException("Timetable generation job not found"));}
    private JobRow mapJob(java.sql.ResultSet rs)throws java.sql.SQLException{return new JobRow(rs.getLong("id"),rs.getLong("organization_id"),rs.getLong("academic_year_id"),rs.getString("request_key"),JobStatus.valueOf(rs.getString("status")),rs.getInt("proposal_count"),rs.getInt("completed_proposals"),rs.getLong("seed"),rs.getInt("solve_time_seconds"),(Long)rs.getObject("source_draft_id"),rs.getBoolean("prefer_class_teacher_first"),rs.getBoolean("cancel_requested"),rs.getString("failure_message"),rs.getLong("created_by_user_id"),rs.getObject("created_at",OffsetDateTime.class),rs.getObject("started_at",OffsetDateTime.class),rs.getObject("finished_at",OffsetDateTime.class),rs.getLong("version"));}
    private void requireYear(long org,long year){if(jdbc.sql("SELECT COUNT(*) FROM academic_years WHERE id=:year AND organization_id=:org").param("year",year).param("org",org).query(Long.class).single()==0)throw new TimetableConfigurationNotFoundException("Academic year not found");}
    private TransactionTemplate transaction(){return new TransactionTemplate(transactionManager);}
    private Schedule copy(Schedule source){List<Lesson> lessons=source.getLessons().stream().map(l->new Lesson(l.getId(),l.getSectionId(),l.getGradeLevelId(),l.getSubjectId(),l.isSupervisedActivity(),l.getActivityName(),l.getMaximumPerDay(),l.getRequiredDoubleBlocks(),l.isRequiredEachDay(),l.getClassTeacherId(),l.isPinned(),l.getOptions(),l.isPinned()?l.getOption():null)).toList();return new Schedule(new ArrayList<>(lessons),source.getTeacherPolicies(),source.isPreferClassTeacherFirst());}
    private Map<String,Long> scoreBreakdown(HardSoftScore score,Schedule schedule){Map<String,Long> values=new LinkedHashMap<>();values.put("hardConstraintScore",score==null?-1:score.hardScore());values.put("softPreferenceScore",score==null?0:score.softScore());if(schedule!=null){values.put("assignedLessons",schedule.getLessons().stream().filter(l->l.getOption()!=null).count());values.put("preferredAvailabilityAssignments",schedule.getLessons().stream().filter(l->l.getOption()!=null&&l.getOption().preferredTeacherTime()).count());values.put("discouragedAvailabilityAssignments",schedule.getLessons().stream().filter(l->l.getOption()!=null&&l.getOption().discouragedTeacherTime()).count());values.put("pinnedLessons",schedule.getLessons().stream().filter(Lesson::isPinned).count());}return Map.copyOf(values);}
    private String json(Object value){try{return objectMapper.writeValueAsString(value);}catch(Exception exception){throw new IllegalStateException("Unable to serialize generation result",exception);}}
    private List<String> jsonStrings(String value){try{return value==null?List.of():objectMapper.readValue(value,objectMapper.getTypeFactory().constructCollectionType(List.class,String.class));}catch(Exception exception){return List.of("Stored result could not be decoded");}}
    private Map<String,Long> jsonLongMap(String value){try{return value==null?Map.of():objectMapper.readValue(value,objectMapper.getTypeFactory().constructMapType(Map.class,String.class,Long.class));}catch(Exception exception){return Map.of();}}
    private List<Conflict> jsonConflicts(String value){try{return value==null?List.of():objectMapper.readValue(value,objectMapper.getTypeFactory().constructCollectionType(List.class,Conflict.class));}catch(Exception exception){return List.of();}}
    private String safeMessage(Exception exception){String message=exception.getMessage();return message==null?"Timetable generation failed":message.substring(0,Math.min(500,message.length()));}

    private record Requirement(long section,long grade,long subject,int weekly,boolean requiredDaily,int daily,int doubles,String name){}
    private record RoomChoice(Long id,boolean exclusive){}
    private record BuildResult(Schedule schedule,List<String> unmet){}
    private record SaveResult(Long draft,List<Conflict> findings,List<String> unmet){}
    private record JobRow(long id,long org,long year,String key,JobStatus status,int count,int completed,long seed,int seconds,Long source,boolean prefer,boolean cancel,String failure,long actor,OffsetDateTime created,OffsetDateTime started,OffsetDateTime finished,long version){}
}
