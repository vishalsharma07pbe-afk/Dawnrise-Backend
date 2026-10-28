package com.dawnrise.academic.timetable.generation;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ai.timefold.solver.core.api.score.HardSoftScore;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TimetablePlanningModel {
    private TimetablePlanningModel() {}

    public record SlotOption(long intervalId, int dayOfWeek, LocalTime startTime, LocalTime endTime,
                             long wingId, Long teacherUserId, Long roomId, boolean exclusiveRoom,
                             boolean preferredTeacherTime, boolean discouragedTeacherTime,
                             boolean firstTeachingPeriod) {
        public boolean overlaps(SlotOption other) {
            return dayOfWeek == other.dayOfWeek && startTime.isBefore(other.endTime)
                    && endTime.isAfter(other.startTime);
        }
    }

    public record TeacherPolicy(int maximumPerDay, int maximumPerWeek,
                                int maximumConsecutive, int minimumRestMinutes) {}

    @PlanningEntity
    public static class Lesson {
        private long id;
        private long sectionId;
        private long gradeLevelId;
        private long subjectId;
        private boolean supervisedActivity;
        private String activityName;
        private int maximumPerDay;
        private int requiredDoubleBlocks;
        private boolean consecutivePeriodsAllowed;
        private boolean requiredEachDay;
        private Long classTeacherId;
        private boolean pinned;
        private List<SlotOption> options = new ArrayList<>();
        private SlotOption option;

        public Lesson() {}

        public Lesson(long id, long sectionId, long gradeLevelId, long subjectId, boolean supervisedActivity, String activityName,
                      int maximumPerDay, int requiredDoubleBlocks, boolean consecutivePeriodsAllowed, boolean requiredEachDay,
                      Long classTeacherId, boolean pinned, List<SlotOption> options, SlotOption option) {
            this.id=id; this.sectionId=sectionId; this.gradeLevelId=gradeLevelId; this.subjectId=subjectId;this.supervisedActivity=supervisedActivity;
            this.activityName=activityName; this.maximumPerDay=maximumPerDay;
            this.requiredDoubleBlocks=requiredDoubleBlocks; this.consecutivePeriodsAllowed=consecutivePeriodsAllowed; this.requiredEachDay=requiredEachDay;
            this.classTeacherId=classTeacherId; this.pinned=pinned; this.options=options; this.option=option;
        }
        public Lesson(long id, long sectionId, long gradeLevelId, long subjectId, boolean supervisedActivity, String activityName,
                      int maximumPerDay, int requiredDoubleBlocks, boolean requiredEachDay,
                      Long classTeacherId, boolean pinned, List<SlotOption> options, SlotOption option) {
            this(id,sectionId,gradeLevelId,subjectId,supervisedActivity,activityName,maximumPerDay,
                    requiredDoubleBlocks,false,requiredEachDay,classTeacherId,pinned,options,option);
        }
        public Lesson(long id,long sectionId,long gradeLevelId,long subjectId,String activityName,
                      int maximumPerDay,int requiredDoubleBlocks,boolean requiredEachDay,
                      Long classTeacherId,boolean pinned,List<SlotOption> options,SlotOption option){
            this(id,sectionId,gradeLevelId,subjectId,false,activityName,maximumPerDay,requiredDoubleBlocks,false,
                    requiredEachDay,classTeacherId,pinned,options,option);
        }
        public long getId(){return id;} public void setId(long value){id=value;}
        public long getSectionId(){return sectionId;} public void setSectionId(long value){sectionId=value;}
        public long getGradeLevelId(){return gradeLevelId;} public void setGradeLevelId(long value){gradeLevelId=value;}
        public long getSubjectId(){return subjectId;} public void setSubjectId(long value){subjectId=value;}
        public boolean isSupervisedActivity(){return supervisedActivity;} public void setSupervisedActivity(boolean value){supervisedActivity=value;}
        public String getActivityName(){return activityName;} public void setActivityName(String value){activityName=value;}
        public int getMaximumPerDay(){return maximumPerDay;} public void setMaximumPerDay(int value){maximumPerDay=value;}
        public int getRequiredDoubleBlocks(){return requiredDoubleBlocks;} public void setRequiredDoubleBlocks(int value){requiredDoubleBlocks=value;}
        public boolean isConsecutivePeriodsAllowed(){return consecutivePeriodsAllowed;} public void setConsecutivePeriodsAllowed(boolean value){consecutivePeriodsAllowed=value;}
        public boolean isRequiredEachDay(){return requiredEachDay;} public void setRequiredEachDay(boolean value){requiredEachDay=value;}
        public Long getClassTeacherId(){return classTeacherId;} public void setClassTeacherId(Long value){classTeacherId=value;}
        @PlanningPin public boolean isPinned(){return pinned;} public void setPinned(boolean value){pinned=value;}
        @ValueRangeProvider(id="slotOptions") public List<SlotOption> getOptions(){return options;} public void setOptions(List<SlotOption> value){options=value;}
        @PlanningVariable(valueRangeProviderRefs="slotOptions", allowsUnassigned=true)
        public SlotOption getOption(){return option;} public void setOption(SlotOption value){option=value;}
    }

    @PlanningSolution
    public static class Schedule {
        private List<Lesson> lessons = new ArrayList<>();
        private Map<Long,TeacherPolicy> teacherPolicies = Map.of();
        private boolean preferClassTeacherFirst;
        private long variationSeed;
        private HardSoftScore score;
        public Schedule() {}
        public Schedule(List<Lesson> lessons, Map<Long,TeacherPolicy> teacherPolicies,
                        boolean preferClassTeacherFirst){this.lessons=lessons;this.teacherPolicies=teacherPolicies;this.preferClassTeacherFirst=preferClassTeacherFirst;}
        @PlanningEntityCollectionProperty public List<Lesson> getLessons(){return lessons;} public void setLessons(List<Lesson> value){lessons=value;}
        public Map<Long,TeacherPolicy> getTeacherPolicies(){return teacherPolicies;} public void setTeacherPolicies(Map<Long,TeacherPolicy> value){teacherPolicies=value;}
        public boolean isPreferClassTeacherFirst(){return preferClassTeacherFirst;} public void setPreferClassTeacherFirst(boolean value){preferClassTeacherFirst=value;}
        public long getVariationSeed(){return variationSeed;} public void setVariationSeed(long value){variationSeed=value;}
        @PlanningScore public HardSoftScore getScore(){return score;} public void setScore(HardSoftScore value){score=value;}
    }
}
