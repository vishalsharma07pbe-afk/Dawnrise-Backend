package com.dawnrise.academic.notificationdelivery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import java.util.Set;
@Service
public class AcademicAudienceResolver {
    private final JdbcClient jdbc;
    public AcademicAudienceResolver(JdbcClient jdbc){this.jdbc=jdbc;}
    public Set<Long> resolve(long organizationId,String type,long id){
        if(organizationId<=0||id<=0)throw new IllegalArgumentException("Audience identifiers must be positive");
        String predicate=switch(type){case "SECTION"->"e.section_id=:id";case "GRADE"->"e.grade_level_id=:id";case "WING"->"EXISTS(SELECT 1 FROM timetable_grade_wings w WHERE w.organization_id=e.organization_id AND w.academic_year_id=e.academic_year_id AND w.grade_level_id=e.grade_level_id AND w.wing_id=:id)";default->throw new IllegalArgumentException("Unsupported academic audience");};
        return Set.copyOf(jdbc.sql("SELECT DISTINCT e.student_user_id FROM student_enrollments e WHERE e.organization_id=:org AND e.status='ENROLLED' AND "+predicate).param("org",organizationId).param("id",id).query(Long.class).list());
    }
}
