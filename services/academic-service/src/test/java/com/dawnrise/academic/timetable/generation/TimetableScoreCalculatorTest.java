package com.dawnrise.academic.timetable.generation;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import com.dawnrise.academic.timetable.generation.TimetablePlanningModel.Lesson;
import com.dawnrise.academic.timetable.generation.TimetablePlanningModel.Schedule;
import com.dawnrise.academic.timetable.generation.TimetablePlanningModel.SlotOption;
import com.dawnrise.academic.timetable.generation.TimetablePlanningModel.TeacherPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimetableScoreCalculatorTest {
    private final TimetableScoreCalculator calculator=new TimetableScoreCalculator();

    @Test
    void acceptsCompleteNonConflictingSchedule(){
        Lesson first=lesson(1,10,100,option(1,1,"09:00","09:40",1,50));
        Lesson second=lesson(2,11,101,option(2,1,"09:40","10:20",2,50));
        assertTrue(calculator.calculateScore(schedule(first,second)).isFeasible());
    }

    @Test
    void detectsCrossWingTeacherOverlapUsingActualTimes(){
        Lesson junior=lesson(1,10,100,option(1,1,"09:00","09:40",1,50));
        Lesson senior=lesson(2,11,101,option(2,1,"09:20","10:00",2,50));
        assertTrue(calculator.calculateScore(schedule(junior,senior)).hardScore()<0);
    }

    @Test
    void treatsUnassignedRequirementAsInfeasible(){
        Lesson lesson=lesson(1,10,100,null);
        assertTrue(calculator.calculateScore(schedule(lesson)).hardScore()<0);
    }

    @Test
    void preservesPinnedPlanningValueInIndependentCopies(){
        SlotOption fixed=option(1,1,"09:00","09:40",1,50);
        Lesson pinned=new Lesson(1,10,1,100,"Math",1,0,false,null,true,List.of(fixed),fixed);
        assertTrue(pinned.isPinned());assertEquals(fixed,pinned.getOption());
    }

    @Test
    void rewardsPreferredTeacherAvailability(){
        SlotOption preferred=new SlotOption(1,1,LocalTime.of(9,0),LocalTime.of(9,40),1,50L,null,false,true,false,true);
        SlotOption neutral=new SlotOption(2,2,LocalTime.of(9,0),LocalTime.of(9,40),1,50L,null,false,false,false,true);
        assertTrue(calculator.calculateScore(schedule(lesson(1,10,100,preferred))).softScore()
                >calculator.calculateScore(schedule(lesson(1,10,100,neutral))).softScore());
    }

    @Test
    void timefoldSolvesDeterministicallyAndKeepsPins(){
        SlotOption fixed=option(1,1,"09:00","09:40",1,50);SlotOption available=option(2,1,"09:40","10:20",1,50);
        Lesson pinned=new Lesson(1,10,1,100,"Math",1,0,false,null,true,List.of(fixed),fixed);
        Lesson unassigned=new Lesson(2,11,1,101,"English",1,0,false,null,false,List.of(available),null);
        Schedule problem=new Schedule(new java.util.ArrayList<>(List.of(pinned,unassigned)),Map.of(50L,new TeacherPolicy(8,40,8,0)),false);
        var solver=SolverFactory.<Schedule>create(new SolverConfig().withSolutionClass(Schedule.class).withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig().withEasyScoreCalculatorClass(TimetableScoreCalculator.class))
                .withTerminationSpentLimit(Duration.ofMillis(100)).withRandomSeed(123L)).buildSolver();
        Schedule solved=solver.solve(problem);
        assertEquals(fixed,solved.getLessons().getFirst().getOption());
        assertEquals(available,solved.getLessons().get(1).getOption());
        assertTrue(solved.getScore().isFeasible());
    }

    private Schedule schedule(Lesson... lessons){return new Schedule(List.of(lessons),Map.of(50L,new TeacherPolicy(8,40,8,0)),false);}
    private Lesson lesson(long id,long section,long subject,SlotOption option){return new Lesson(id,section,1,subject,"Subject",1,0,false,null,false,option==null?List.of(option(99,1,"10:00","10:40",1,50)):List.of(option),option);}
    private SlotOption option(long id,int day,String start,String end,long wing,long teacher){return new SlotOption(id,day,LocalTime.parse(start),LocalTime.parse(end),wing,teacher,null,false,false,false,id==1);}
}
