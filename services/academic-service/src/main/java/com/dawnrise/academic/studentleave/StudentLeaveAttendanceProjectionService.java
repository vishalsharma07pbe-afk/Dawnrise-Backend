package com.dawnrise.academic.studentleave;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StudentLeaveAttendanceProjectionService {
    private final NamedParameterJdbcTemplate jdbc;

    @Autowired
    public StudentLeaveAttendanceProjectionService(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = jdbc.getIfAvailable();
    }

    StudentLeaveAttendanceProjectionService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<Long, AttendanceStatus> approvedForSectionDate(long organizationId, long academicYearId,
                                                               long gradeLevelId, long sectionId, LocalDate attendanceDate) {
        if (jdbc == null) return Map.of();
        var parameters = new MapSqlParameterSource("organizationId", organizationId)
                .addValue("academicYearId", academicYearId).addValue("gradeLevelId", gradeLevelId)
                .addValue("sectionId", sectionId).addValue("attendanceDate", attendanceDate);
        return jdbc.query("""
                SELECT r.student_user_id,p.projected_status
                  FROM student_leave_attendance_projection p
                  JOIN student_leave_requests r ON r.id=p.request_id AND r.organization_id=p.organization_id
                 WHERE p.organization_id=:organizationId AND r.organization_id=:organizationId
                   AND r.academic_year_id=:academicYearId AND r.grade_level_id=:gradeLevelId
                   AND r.section_id=:sectionId AND r.status='APPROVED'
                   AND p.attendance_date=:attendanceDate
                """, parameters, (rs, row) -> Map.entry(rs.getLong(1),
                        "HALF_DAY_EXCUSED".equals(rs.getString(2)) ? AttendanceStatus.HALF_DAY : AttendanceStatus.EXCUSED))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, ignored) -> first));
    }

    @Transactional
    public AttendanceStatus apply(long organizationId, long studentUserId, LocalDate attendanceDate,
                                  AttendanceStatus requestedStatus) {
        if (jdbc == null) return requestedStatus;
        var parameters = new MapSqlParameterSource("organizationId", organizationId)
                .addValue("studentUserId", studentUserId)
                .addValue("attendanceDate", attendanceDate);
        List<Projection> projections = jdbc.query("""
                SELECT p.id,p.projected_status
                  FROM student_leave_attendance_projection p
                  JOIN student_leave_requests r ON r.id=p.request_id AND r.organization_id=p.organization_id
                 WHERE p.organization_id=:organizationId AND r.student_user_id=:studentUserId
                   AND r.status='APPROVED' AND p.attendance_date=:attendanceDate
                   AND p.conflict_status IN ('NONE','PRESENT_RECORDED','PARTIAL_RECORDED')
                 ORDER BY p.id FOR UPDATE
                """, parameters, (rs, row) -> new Projection(rs.getLong(1), rs.getString(2)));
        if (projections.isEmpty()) return requestedStatus;
        Projection projection = projections.getFirst();
        AttendanceStatus leaveStatus = "HALF_DAY_EXCUSED".equals(projection.status())
                ? AttendanceStatus.HALF_DAY : AttendanceStatus.EXCUSED;
        jdbc.update("UPDATE student_leave_attendance_projection SET conflict_status='NONE' WHERE id=:id",
                new MapSqlParameterSource("id", projection.id()));
        return leaveStatus;
    }

    record Projection(long id, String status) {}
}
