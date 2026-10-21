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
import java.util.List;
import java.util.Map;

public class TimetableScoreCalculator implements EasyScoreCalculator<Schedule, HardSoftScore> {
    @Override
    public HardSoftScore calculateScore(Schedule schedule) {
        int hard=0,soft=0;
        List<Lesson> assigned=schedule.getLessons().stream().filter(l->l.getOption()!=null).toList();
        hard-=schedule.getLessons().size()-assigned.size();
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
            hard-=Math.abs(doubles-sample.getRequiredDoubleBlocks());
        }
        Map<Long,List<SlotOption>> byTeacher=new HashMap<>();
        for(Lesson lesson:assigned){SlotOption option=lesson.getOption();if(option.teacherUserId()!=null){byTeacher.computeIfAbsent(option.teacherUserId(),key->new ArrayList<>()).add(option);if(option.preferredTeacherTime())soft+=3;if(option.discouragedTeacherTime())soft-=3;if(schedule.isPreferClassTeacherFirst()&&option.firstTeachingPeriod()&&option.teacherUserId().equals(lesson.getClassTeacherId()))soft+=4;}}
        for(var entry:byTeacher.entrySet()){
            TeacherPolicy policy=schedule.getTeacherPolicies().get(entry.getKey());if(policy==null)continue;
            List<SlotOption> options=entry.getValue();if(options.size()>policy.maximumPerWeek())hard-=options.size()-policy.maximumPerWeek();
            Map<Integer,List<SlotOption>> days=new HashMap<>();options.forEach(o->days.computeIfAbsent(o.dayOfWeek(),key->new ArrayList<>()).add(o));
            for(List<SlotOption> day:days.values()){
                day.sort(Comparator.comparing(SlotOption::startTime));if(day.size()>policy.maximumPerDay())hard-=day.size()-policy.maximumPerDay();int consecutive=1;
                for(int i=1;i<day.size();i++){long gap=Duration.between(day.get(i-1).endTime(),day.get(i).startTime()).toMinutes();if(gap==0)consecutive++;else consecutive=1;if(consecutive>policy.maximumConsecutive())hard--;if(gap>0&&gap<policy.minimumRestMinutes())hard--;if(gap>=policy.minimumRestMinutes())soft+=Math.min(3,(int)(gap/15));}
            }
        }
        if(byTeacher.size()>1){int minimum=byTeacher.values().stream().mapToInt(List::size).min().orElse(0);int maximum=byTeacher.values().stream().mapToInt(List::size).max().orElse(0);soft-=maximum-minimum;}
        return HardSoftScore.of(hard,soft);
    }
    private int doubleBlocks(List<SlotOption> source){List<SlotOption> options=new ArrayList<>(source);options.sort(Comparator.comparingInt(SlotOption::dayOfWeek).thenComparing(SlotOption::startTime));int blocks=0,run=1;for(int i=1;i<options.size();i++){SlotOption a=options.get(i-1),b=options.get(i);if(a.dayOfWeek()==b.dayOfWeek()&&a.endTime().equals(b.startTime()))run++;else{blocks+=run/2;run=1;}}return options.isEmpty()?0:blocks+run/2;}
}
