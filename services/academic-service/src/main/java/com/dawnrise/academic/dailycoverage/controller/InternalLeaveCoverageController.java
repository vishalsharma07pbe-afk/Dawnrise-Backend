package com.dawnrise.academic.dailycoverage.controller;

import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.AbsenceType;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.ReasonedVersionRequest;
import com.dawnrise.academic.dailycoverage.dto.DailyCoverageDtos.RecordAbsenceRequest;
import com.dawnrise.academic.dailycoverage.service.DailyCoverageService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/internal/v1/organizations/{organizationId}/staff-leave-coverage")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalLeaveCoverageController {
    private final DailyCoverageService coverage; private final JdbcClient jdbc;
    public InternalLeaveCoverageController(DailyCoverageService coverage,JdbcClient jdbc){this.coverage=coverage;this.jdbc=jdbc;}

    @PostMapping
    public Result apply(@PathVariable long organizationId,@RequestBody Request request){
        int changed=0;
        for(LocalDate date:request.workingDates()){
            String key="staff-leave:"+request.leaveRequestId()+":"+date;
            if("CANCEL".equals(request.operation())){
                var row=jdbc.sql("SELECT id,version,absence_date FROM timetable_teacher_absences WHERE organization_id=:org AND idempotency_key=:key AND status='CONFIRMED'")
                        .param("org",organizationId).param("key",key).query((rs,n)->new Existing(rs.getLong(1),rs.getLong(2),rs.getObject(3,LocalDate.class))).optional();
                if(row.isPresent()&&!row.get().date().isBefore(LocalDate.now())){coverage.cancelAbsence(organizationId,request.actorUserId(),row.get().id(),new ReasonedVersionRequest(row.get().version(),"Approved staff leave was cancelled or changed",request.sourceEventId()+":"+date));changed++;}
            }else{
                TimeRange range=range(organizationId,request.teacherUserId(),date,request.kind(),request.halfDayPeriod(),request.startTime(),request.endTime());
                coverage.recordAbsence(organizationId,request.actorUserId(),new RecordAbsenceRequest(request.teacherUserId(),date,type(request.kind()),range.start(),range.end(),List.of(),"Approved staff leave",key));changed++;
            }
        }
        return new Result(changed);
    }
    private TimeRange range(long org,long teacher,LocalDate date,String kind,String half,String start,String end){
        if(List.of("LATE_ARRIVAL","EARLY_DEPARTURE","PARTIAL_TIME").contains(kind))return new TimeRange(LocalTime.parse(start),LocalTime.parse(end));
        if(!"HALF_DAY".equals(kind))return new TimeRange(null,null);
        List<TimeRange> slots=jdbc.sql("""
                SELECT e.start_time,e.end_time FROM timetable_publication_entries e JOIN timetable_publications p ON p.id=e.publication_id AND p.organization_id=e.organization_id
                WHERE e.organization_id=:org AND e.teacher_user_id=:teacher AND e.day_of_week=:day AND p.effective_from<=:date AND (p.effective_to_exclusive IS NULL OR p.effective_to_exclusive>:date)
                ORDER BY e.start_time
                """).param("org",org).param("teacher",teacher).param("day",date.getDayOfWeek().getValue()).param("date",date)
                .query((rs,n)->new TimeRange(rs.getObject(1,LocalTime.class),rs.getObject(2,LocalTime.class))).list();
        if(slots.isEmpty())return new TimeRange(null,null);int split=Math.max(1,slots.size()/2);
        return "AFTERNOON".equals(half)?new TimeRange(slots.get(Math.min(slots.size()-1,split)).start(),slots.getLast().end()):new TimeRange(slots.getFirst().start(),slots.get(split-1).end());
    }
    private AbsenceType type(String kind){return switch(kind){case "LATE_ARRIVAL"->AbsenceType.LATE_ARRIVAL;case "EARLY_DEPARTURE"->AbsenceType.EARLY_DEPARTURE;default->AbsenceType.KNOWN_LEAVE;};}
    public record Request(long leaveRequestId,long teacherUserId,long actorUserId,String operation,List<LocalDate> workingDates,String startTime,String endTime,String halfDayPeriod,String kind,String sourceEventId){}
    public record Result(int changed){} private record TimeRange(LocalTime start,LocalTime end){} private record Existing(long id,long version,LocalDate date){}
}
