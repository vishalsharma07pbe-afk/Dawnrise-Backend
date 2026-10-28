package com.dawnrise.academic.timetable.generation;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.calculator.EasyScoreCalculator;
import com.dawnrise.academic.timetable.generation.TimetablePlanningModel.Lesson;
import com.dawnrise.academic.timetable.generation.TimetablePlanningModel.Schedule;
import com.dawnrise.academic.timetable.generation.TimetablePlanningModel.SlotOption;
import com.dawnrise.academic.timetable.generation.TimetablePlanningModel.TeacherPolicy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class TimetableScoreCalculator implements EasyScoreCalculator<Schedule, HardSoftScore> {
    private static final int MAX_CHANGEOVER_MINUTES = 10;
    private static final int UNASSIGNED_LESSON_PENALTY = 1_000;
    @Override
    public HardSoftScore calculateScore(Schedule schedule) {
        int hard=0,soft=0;
        List<Lesson> assigned=schedule.getLessons().stream().filter(l->l.getOption()!=null).toList();
        // A partially built subject distribution temporarily violates rules such as
        // "required every day" and exact double blocks. Leaving a lesson unassigned
        // must therefore be substantially worse, otherwise the solver can settle on
        // an empty timetable because assigning its first lesson does not improve score.
        hard-=UNASSIGNED_LESSON_PENALTY*(schedule.getLessons().size()-assigned.size());
        for(int i=0;i<assigned.size();i++)for(int j=i+1;j<assigned.size();j++){
            Lesson a=assigned.get(i),b=assigned.get(j);SlotOption x=a.getOption(),y=b.getOption();
            if(!x.overlaps(y))continue;
            if(a.getSectionId()==b.getSectionId())hard--;
            if(x.teacherUserId()!=null&&x.teacherUserId().equals(y.teacherUserId()))hard--;
            if(x.exclusiveRoom()&&y.exclusiveRoom()&&x.roomId()!=null&&x.roomId().equals(y.roomId()))hard--;
        }
        Map<String,List<Lesson>> subjectGroups=new HashMap<>();
        for(Lesson lesson:assigned)if(!lesson.isSupervisedActivity())subjectGroups.computeIfAbsent(lesson.getSectionId()+":"+lesson.getSubjectId(),key->new ArrayList<>()).add(lesson);
        for(List<Lesson> lessons:subjectGroups.values()){
            Lesson sample=lessons.getFirst();Map<Integer,Integer> perDay=new HashMap<>();
            lessons.forEach(l->perDay.merge(l.getOption().dayOfWeek(),1,Integer::sum));
            for(int count:perDay.values()){if(count>sample.getMaximumPerDay())hard-=count-sample.getMaximumPerDay();if(count>1)soft-=count-1;}
            if(sample.isRequiredEachDay()){
                long availableDays=sample.getOptions().stream().map(SlotOption::dayOfWeek).distinct().count();
                if(perDay.size()<availableDays)hard-=(int)(availableDays-perDay.size());
            }
            int doubles=doubleBlocks(lessons.stream().map(Lesson::getOption).toList());
            if(sample.getRequiredDoubleBlocks()>0)hard-=Math.abs(doubles-sample.getRequiredDoubleBlocks());
            else if(!sample.isConsecutivePeriodsAllowed())hard-=doubles;
        }
        Map<Long,List<SlotOption>> byTeacher=new HashMap<>();
        for(Lesson lesson:assigned){SlotOption option=lesson.getOption();if(option.teacherUserId()!=null){byTeacher.computeIfAbsent(option.teacherUserId(),key->new ArrayList<>()).add(option);if(option.preferredTeacherTime())soft+=3;if(option.discouragedTeacherTime())soft-=3;if(schedule.isPreferClassTeacherFirst()&&option.firstTeachingPeriod()&&option.teacherUserId().equals(lesson.getClassTeacherId()))soft+=4;}}
        for(var entry:byTeacher.entrySet()){
            TeacherPolicy policy=schedule.getTeacherPolicies().get(entry.getKey());if(policy==null)continue;
            List<SlotOption> options=entry.getValue();if(options.size()>policy.maximumPerWeek())hard-=options.size()-policy.maximumPerWeek();
            Map<Integer,List<SlotOption>> days=new HashMap<>();options.forEach(o->days.computeIfAbsent(o.dayOfWeek(),key->new ArrayList<>()).add(o));
            for(List<SlotOption> day:days.values()){
                day.sort(Comparator.comparing(SlotOption::startTime));if(day.size()>policy.maximumPerDay())hard-=day.size()-policy.maximumPerDay();int consecutive=1;
                for(int i=1;i<day.size();i++){
                    long gap=Duration.between(day.get(i-1).endTime(),day.get(i).startTime()).toMinutes();
                    boolean meaningfulBreak=policy.minimumRestMinutes()<=0?gap>MAX_CHANGEOVER_MINUTES:gap>=policy.minimumRestMinutes();
                    if(meaningfulBreak)consecutive=1;else consecutive++;
                    if(consecutive>policy.maximumConsecutive())hard--;
                    if(meaningfulBreak)soft+=Math.min(3,(int)(gap/15));
                }
            }
        }
        if(byTeacher.size()>1){int minimum=byTeacher.values().stream().mapToInt(List::size).min().orElse(0);int maximum=byTeacher.values().stream().mapToInt(List::size).max().orElse(0);soft-=maximum-minimum;}
        // Preserve every real preference as the higher-order score. The seed-based
        // suffix only selects between otherwise equivalent valid arrangements, so a
        // newly requested timetable can genuinely differ without weakening rules.
        int variation=variationScore(schedule,assigned);
        return HardSoftScore.of(hard,soft*1_000+variation);
    }

    private int variationScore(Schedule schedule,List<Lesson> assigned){
        long hash=schedule.getVariationSeed()^0x9E3779B97F4A7C15L;
        for(Lesson lesson:assigned.stream().sorted(Comparator.comparingLong(Lesson::getId)).toList()){
            SlotOption option=lesson.getOption();
            hash^=lesson.getId()*0xBF58476D1CE4E5B9L;
            hash=Long.rotateLeft(hash,13)^option.intervalId();
            hash=Long.rotateLeft(hash,17)^option.dayOfWeek();
            hash=Long.rotateLeft(hash,11)^(option.teacherUserId()==null?0:option.teacherUserId());
            hash=Long.rotateLeft(hash,7)^(option.roomId()==null?0:option.roomId());
        }
        return Math.floorMod((int)(hash^(hash>>>32)),1_000);
    }

    public List<String> explainHardConstraintViolations(Schedule schedule) {
        Set<String> messages=new LinkedHashSet<>();
        List<Lesson> assigned=schedule.getLessons().stream().filter(l->l.getOption()!=null).toList();
        Map<String,Long> unassigned=schedule.getLessons().stream().filter(l->l.getOption()==null)
                .collect(java.util.stream.Collectors.groupingBy(l->"Section "+l.getSectionId()+" · "+l.getActivityName(),LinkedHashMap::new,java.util.stream.Collectors.counting()));
        unassigned.forEach((label,count)->messages.add(label+" still needs "+count+" period"+(count==1?"":"s")+". Check its teacher availability, daily limit and classroom."));
        for(int i=0;i<assigned.size();i++)for(int j=i+1;j<assigned.size();j++){
            Lesson a=assigned.get(i),b=assigned.get(j);SlotOption x=a.getOption(),y=b.getOption();if(!x.overlaps(y))continue;
            String when=" on day "+x.dayOfWeek()+" at "+x.startTime();
            if(a.getSectionId()==b.getSectionId())messages.add("Section "+a.getSectionId()+" has two periods at the same time"+when+".");
            if(x.teacherUserId()!=null&&x.teacherUserId().equals(y.teacherUserId()))messages.add("Teacher "+x.teacherUserId()+" is assigned to more than one class"+when+".");
            if(x.exclusiveRoom()&&y.exclusiveRoom()&&x.roomId()!=null&&x.roomId().equals(y.roomId()))messages.add("A special classroom is assigned to more than one class"+when+".");
        }
        Map<String,List<Lesson>> subjectGroups=new HashMap<>();
        for(Lesson lesson:assigned)if(!lesson.isSupervisedActivity())subjectGroups.computeIfAbsent(lesson.getSectionId()+":"+lesson.getSubjectId(),key->new ArrayList<>()).add(lesson);
        for(List<Lesson> lessons:subjectGroups.values()){
            Lesson sample=lessons.getFirst();Map<Integer,Integer> perDay=new HashMap<>();lessons.forEach(l->perDay.merge(l.getOption().dayOfWeek(),1,Integer::sum));
            if(perDay.values().stream().anyMatch(count->count>sample.getMaximumPerDay()))messages.add(sample.getActivityName()+" exceeds its daily limit for Section "+sample.getSectionId()+".");
            if(sample.isRequiredEachDay()){
                long availableDays=sample.getOptions().stream().map(SlotOption::dayOfWeek).distinct().count();
                if(perDay.size()<availableDays)messages.add(sample.getActivityName()+" cannot be placed on every working day for Section "+sample.getSectionId()+".");
            }
            int doubles=doubleBlocks(lessons.stream().map(Lesson::getOption).toList());
            if(sample.getRequiredDoubleBlocks()>0&&doubles!=sample.getRequiredDoubleBlocks())messages.add(sample.getActivityName()+" needs "+sample.getRequiredDoubleBlocks()+" double period block"+(sample.getRequiredDoubleBlocks()==1?"":"s")+" for Section "+sample.getSectionId()+".");
            if(!sample.isConsecutivePeriodsAllowed()&&doubles>0)messages.add(sample.getActivityName()+" has consecutive periods although they are not allowed for Section "+sample.getSectionId()+".");
        }
        Map<Long,List<SlotOption>> byTeacher=new HashMap<>();assigned.forEach(l->{if(l.getOption().teacherUserId()!=null)byTeacher.computeIfAbsent(l.getOption().teacherUserId(),key->new ArrayList<>()).add(l.getOption());});
        byTeacher.forEach((teacher,options)->{
            TeacherPolicy policy=schedule.getTeacherPolicies().get(teacher);if(policy==null)return;
            if(options.size()>policy.maximumPerWeek())messages.add("Teacher "+teacher+" exceeds the weekly teaching limit.");
            Map<Integer,List<SlotOption>> days=new HashMap<>();options.forEach(o->days.computeIfAbsent(o.dayOfWeek(),key->new ArrayList<>()).add(o));
            if(days.values().stream().anyMatch(day->day.size()>policy.maximumPerDay()))messages.add("Teacher "+teacher+" exceeds the daily teaching limit.");
            for(List<SlotOption> day:days.values()){
                day.sort(Comparator.comparing(SlotOption::startTime));int consecutive=1;
                for(int i=1;i<day.size();i++){long gap=Duration.between(day.get(i-1).endTime(),day.get(i).startTime()).toMinutes();boolean meaningfulBreak=policy.minimumRestMinutes()<=0?gap>MAX_CHANGEOVER_MINUTES:gap>=policy.minimumRestMinutes();consecutive=meaningfulBreak?1:consecutive+1;if(consecutive>policy.maximumConsecutive()){messages.add("Teacher "+teacher+" exceeds the consecutive-period limit.");break;}}
            }
        });
        if(messages.isEmpty())messages.add("The current teacher, classroom and subject rules conflict. Review strict daily, consecutive-period and availability settings, then try again.");
        return messages.stream().limit(8).toList();
    }
    private int doubleBlocks(List<SlotOption> source){List<SlotOption> options=new ArrayList<>(source);options.sort(Comparator.comparingInt(SlotOption::dayOfWeek).thenComparing(SlotOption::startTime));int blocks=0,run=1;for(int i=1;i<options.size();i++){SlotOption a=options.get(i-1),b=options.get(i);long gap=Duration.between(a.endTime(),b.startTime()).toMinutes();if(a.dayOfWeek()==b.dayOfWeek()&&gap>=0&&gap<=MAX_CHANGEOVER_MINUTES)run++;else{blocks+=run/2;run=1;}}return options.isEmpty()?0:blocks+run/2;}
}
