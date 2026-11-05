package com.dawnrise.academic.studentattendance.reporting.service;

import com.dawnrise.academic.results.integration.identity.IdentityGuardianAccessClient;
import com.dawnrise.academic.studentattendance.reporting.dto.GuardianAttendanceSummaryResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
public class GuardianAttendanceSummaryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final IdentityGuardianAccessClient guardians;

    public GuardianAttendanceSummaryService(ObjectProvider<NamedParameterJdbcTemplate> jdbc,
                                             IdentityGuardianAccessClient guardians) {
        this.jdbc = jdbc.getIfAvailable();
        this.guardians = guardians;
    }

    @Transactional(readOnly = true)
    public GuardianAttendanceSummaryResponse get(long organizationId, long guardianUserId, long studentUserId) {
        if (!guardians.hasActiveRelationship(organizationId, guardianUserId, studentUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance summary not found");
        }
        if (jdbc == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Attendance summary is unavailable");
        }
        var parameters = new MapSqlParameterSource("organizationId", organizationId)
                .addValue("studentUserId", studentUserId);
        var totals = jdbc.queryForObject("""
                SELECT COUNT(*), COALESCE(SUM(ar.earned_credit), 0), COALESCE(SUM(ar.possible_credit), 0),
                       COUNT(*) FILTER (WHERE ar.recorded_status='PRESENT'),
                       COUNT(*) FILTER (WHERE ar.recorded_status='ABSENT'),
                       COUNT(*) FILTER (WHERE ar.recorded_status='LATE'),
                       COUNT(*) FILTER (WHERE ar.recorded_status='HALF_DAY'),
                       COUNT(*) FILTER (WHERE ar.recorded_status='EXCUSED')
                FROM student_attendance_records ar
                JOIN student_attendance_sessions s ON s.id=ar.attendance_session_id
                    AND s.organization_id=ar.organization_id
                JOIN academic_years y ON y.id=ar.academic_year_id
                    AND y.organization_id=ar.organization_id
                WHERE ar.organization_id=:organizationId AND ar.student_user_id=:studentUserId
                    AND y.status='ACTIVE' AND s.lifecycle_status='SUBMITTED'
                """, parameters, (rs, row) -> new Totals(rs.getLong(1), rs.getBigDecimal(2),
                        rs.getBigDecimal(3), rs.getLong(4), rs.getLong(5), rs.getLong(6),
                        rs.getLong(7), rs.getLong(8)));
        if (totals == null || totals.recordedDays() == 0) {
            return new GuardianAttendanceSummaryResponse(studentUserId, 0, null, 0, 0, 0, 0, 0, null, null);
        }
        var latest = jdbc.query("""
                SELECT s.attendance_date, ar.recorded_status
                FROM student_attendance_records ar
                JOIN student_attendance_sessions s ON s.id=ar.attendance_session_id
                    AND s.organization_id=ar.organization_id
                JOIN academic_years y ON y.id=ar.academic_year_id
                    AND y.organization_id=ar.organization_id
                WHERE ar.organization_id=:organizationId AND ar.student_user_id=:studentUserId
                    AND y.status='ACTIVE' AND s.lifecycle_status='SUBMITTED'
                ORDER BY s.attendance_date DESC, ar.id DESC LIMIT 1
                """, parameters, (rs, row) -> new Latest(rs.getObject(1, LocalDate.class), rs.getString(2)));
        Double percentage = totals.possibleCredit().signum() == 0 ? null : totals.earnedCredit()
                .multiply(BigDecimal.valueOf(100))
                .divide(totals.possibleCredit(), 1, RoundingMode.HALF_UP)
                .doubleValue();
        return new GuardianAttendanceSummaryResponse(studentUserId, totals.recordedDays(), percentage,
                totals.present(), totals.absent(), totals.late(), totals.halfDay(), totals.excused(),
                latest.getFirst().date(), latest.getFirst().status());
    }

    private record Totals(long recordedDays, BigDecimal earnedCredit, BigDecimal possibleCredit,
                          long present, long absent, long late, long halfDay, long excused) {}
    private record Latest(LocalDate date, String status) {}
}
