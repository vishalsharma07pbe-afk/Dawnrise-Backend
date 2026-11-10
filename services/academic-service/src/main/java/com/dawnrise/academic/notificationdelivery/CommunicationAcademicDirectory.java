package com.dawnrise.academic.notificationdelivery;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class CommunicationAcademicDirectory {
    private final JdbcClient jdbc;
    private final AcademicAudienceResolver resolver;
    public CommunicationAcademicDirectory(JdbcClient jdbc, AcademicAudienceResolver resolver) { this.jdbc = jdbc; this.resolver = resolver; }

    public List<CommunicationAcademicDtos.Group> groups(long organizationId, long actorUserId, boolean schoolWide) {
        validate(organizationId, actorUserId);
        String scope = schoolWide ? "" : " AND EXISTS (SELECT 1 FROM teacher_assignments ta WHERE ta.organization_id=s.organization_id AND ta.academic_year_id=s.academic_year_id AND ta.section_id=s.id AND ta.teacher_user_id=:actor)";
        String sql = "SELECT s.id section_id,s.name section_name,g.id grade_id,g.name grade_name,w.id wing_id,w.name wing_name " +
                "FROM sections s JOIN academic_years y ON y.id=s.academic_year_id AND y.organization_id=s.organization_id AND y.status='ACTIVE' " +
                "JOIN grade_levels g ON g.id=s.grade_level_id AND g.organization_id=s.organization_id " +
                "LEFT JOIN timetable_grade_wings gw ON gw.organization_id=s.organization_id AND gw.academic_year_id=s.academic_year_id AND gw.grade_level_id=s.grade_level_id " +
                "LEFT JOIN timetable_wings w ON w.id=gw.wing_id AND w.organization_id=gw.organization_id " +
                "WHERE s.organization_id=:organizationId" + scope + " ORDER BY g.display_order,s.name";
        var query = jdbc.sql(sql).param("organizationId", organizationId);
        if (!schoolWide) query = query.param("actor", actorUserId);
        List<CommunicationAcademicDtos.Group> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        query.query((rs, row) -> {
            add(result, seen, "SECTION", rs.getLong("section_id"), rs.getString("grade_name") + " · " + rs.getString("section_name"), "Current enrolled students");
            add(result, seen, "SECTION_FAMILIES", rs.getLong("section_id"), rs.getString("grade_name") + " · " + rs.getString("section_name") + " families", "Active linked guardians");
            add(result, seen, "GRADE", rs.getLong("grade_id"), rs.getString("grade_name"), "Current enrolled students");
            add(result, seen, "GRADE_FAMILIES", rs.getLong("grade_id"), rs.getString("grade_name") + " families", "Active linked guardians");
            long wingId = rs.getLong("wing_id");
            if (!rs.wasNull()) {
                add(result, seen, "WING", wingId, rs.getString("wing_name"), "Current enrolled students");
                add(result, seen, "WING_FAMILIES", wingId, rs.getString("wing_name") + " families", "Active linked guardians");
            }
            return 0;
        }).list();
        return List.copyOf(result);
    }

    public Set<Long> scopeStudents(long organizationId, long actorUserId, boolean schoolWide) {
        validate(organizationId, actorUserId);
        String scope = schoolWide ? "" : " AND EXISTS (SELECT 1 FROM teacher_assignments ta WHERE ta.organization_id=e.organization_id AND ta.academic_year_id=e.academic_year_id AND ta.section_id=e.section_id AND ta.teacher_user_id=:actor)";
        var query = jdbc.sql("SELECT DISTINCT e.student_user_id FROM student_enrollments e JOIN academic_years y ON y.id=e.academic_year_id AND y.organization_id=e.organization_id AND y.status='ACTIVE' WHERE e.organization_id=:organizationId AND e.status='ENROLLED'" + scope + " ORDER BY e.student_user_id").param("organizationId", organizationId);
        if (!schoolWide) query = query.param("actor", actorUserId);
        return Set.copyOf(query.query(Long.class).list());
    }

    public Set<Long> resolve(long organizationId, CommunicationAcademicDtos.ResolveRequest request) {
        validate(organizationId, request.actorUserId());
        String baseType = request.type().endsWith("_FAMILIES") ? request.type().substring(0, request.type().length() - 9) : request.type();
        if (!Set.of("SECTION", "GRADE", "WING").contains(baseType) || request.id() <= 0) throw new IllegalArgumentException("Unsupported academic audience");
        if (!request.schoolWide() && groups(organizationId, request.actorUserId(), false).stream().noneMatch(group -> group.type().equals(request.type()) && group.id() == request.id())) throw new IllegalArgumentException("Audience is outside the actor assignment scope");
        Set<Long> students = new LinkedHashSet<>(resolver.resolve(organizationId, baseType, request.id()));
        if (!request.schoolWide()) students.retainAll(scopeStudents(organizationId, request.actorUserId(), false));
        if (students.isEmpty()) throw new IllegalArgumentException("Audience is unavailable or empty");
        return Set.copyOf(students);
    }

    public List<CommunicationAcademicDtos.ParentContact> parentContacts(long organizationId,long studentUserId){
        if(organizationId<=0||studentUserId<=0)throw new IllegalArgumentException("Organization and student are required");
        return jdbc.sql("SELECT DISTINCT ta.teacher_user_id,ta.assignment_type FROM student_enrollments e JOIN academic_years y ON y.id=e.academic_year_id AND y.organization_id=e.organization_id AND y.status='ACTIVE' JOIN teacher_assignments ta ON ta.organization_id=e.organization_id AND ta.academic_year_id=e.academic_year_id AND ta.section_id=e.section_id WHERE e.organization_id=:org AND e.student_user_id=:student AND e.status='ENROLLED' ORDER BY ta.assignment_type,ta.teacher_user_id")
                .param("org",organizationId).param("student",studentUserId).query((rs,row)->new CommunicationAcademicDtos.ParentContact(rs.getLong(1),rs.getString(2))).list();
    }

    private static void add(List<CommunicationAcademicDtos.Group> result, Set<String> seen, String type, long id, String label, String detail) { if (seen.add(type + ':' + id)) result.add(new CommunicationAcademicDtos.Group(type, id, label, detail)); }
    private static void validate(long organizationId, long actorUserId) { if (organizationId <= 0 || actorUserId <= 0) throw new IllegalArgumentException("Organization and actor are required"); }
}
