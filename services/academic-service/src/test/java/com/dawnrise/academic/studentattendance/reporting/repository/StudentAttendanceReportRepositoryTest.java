package com.dawnrise.academic.studentattendance.reporting.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceReportRepositoryTest {

    @Test
    void aggregateQueriesUseSubmittedEffectiveStatusStoredCreditsAndTenantFilters()
            throws Exception {
        assertOfficialAggregateQuery("summarizeSection",
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                LocalDate.class,
                LocalDate.class);
        assertOfficialAggregateQuery("summarizeGrade",
                Long.class,
                Long.class,
                Long.class,
                LocalDate.class,
                LocalDate.class);
        assertOfficialAggregateQuery("summarizeStudent",
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                LocalDate.class,
                LocalDate.class);
        assertOfficialAggregateQuery("summarizeStudentMonth",
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                LocalDate.class,
                LocalDate.class);
    }

    @Test
    void dailyHistoryAndPaginationQueriesAreNativeAndSubmittedOnly()
            throws Exception {
        Method daily = method("findDailySectionRecords",
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                LocalDate.class);
        Method history = method("findStudentHistory",
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                LocalDate.class,
                LocalDate.class,
                Pageable.class);

        assertThat(query(daily).nativeQuery()).isTrue();
        assertThat(query(daily).value())
                .contains("attendance_session.lifecycle_status =")
                .contains("'SUBMITTED'")
                .contains("attendance_record.organization_id =")
                .contains(":organizationId");
        assertFullRecordSessionJoin(query(daily).value());
        assertThat(query(daily).value())
                .contains("enrollment.grade_level_id =")
                .contains("attendance_record.grade_level_id")
                .contains("enrollment.section_id =")
                .contains("attendance_record.section_id");

        assertThat(history.getReturnType()).isEqualTo(Page.class);
        assertThat(query(history).nativeQuery()).isTrue();
        assertThat(query(history).value())
                .contains("attendance_session.lifecycle_status =")
                .contains("'SUBMITTED'")
                .contains("ORDER BY");
        assertFullRecordSessionJoin(query(history).value());
        assertThat(query(history).countQuery())
                .contains("attendance_session.lifecycle_status =")
                .contains("'SUBMITTED'");
        assertFullRecordSessionJoin(query(history).countQuery());
        assertParamNames(history,
                "organizationId",
                "academicYearId",
                "studentUserId",
                "gradeLevelId",
                "sectionId",
                "fromDate",
                "toDate");
    }

    @Test
    void gradeSectionsLowAttendanceAndMissingQueriesKeepExpectedScope()
            throws Exception {
        Method gradeSections = method("summarizeGradeSections",
                Long.class,
                Long.class,
                Long.class,
                LocalDate.class,
                LocalDate.class);
        Method lowAttendance = method("findLowAttendanceStudents",
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                LocalDate.class,
                LocalDate.class,
                BigDecimal.class,
                Pageable.class);
        Method missing = method("findMissingOrIncompleteSessions",
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                LocalDate.class,
                LocalDate.class,
                Pageable.class);

        assertThat(query(gradeSections).value())
                .contains("FROM sections section")
                .contains("LEFT JOIN student_attendance_sessions")
                .contains("attendance_session.lifecycle_status =")
                .contains("'SUBMITTED'")
                .contains("section.organization_id =");

        assertThat(lowAttendance.getReturnType()).isEqualTo(Page.class);
        assertThat(query(lowAttendance).value())
                .contains(":gradeLevelId IS NULL")
                .contains(":sectionId IS NULL")
                .contains("attendance_session.lifecycle_status =")
                .contains("'SUBMITTED'")
                .contains("SUM(attendance_record.possible_credit) > 0");
        assertFullRecordSessionJoin(query(lowAttendance).value());
        assertThat(query(lowAttendance).countQuery())
                .contains(":gradeLevelId IS NULL")
                .contains(":sectionId IS NULL")
                .contains("'SUBMITTED'");
        assertFullRecordSessionJoin(query(lowAttendance).countQuery());

        assertThat(missing.getReturnType()).isEqualTo(Slice.class);
        assertThat(query(missing).value())
                .contains("calendar_day.attendance_requirement =")
                .contains("'REQUIRED'")
                .contains(":gradeLevelId IS NULL")
                .contains(":sectionId IS NULL")
                .contains("report_row.\"attendanceSessionId\" IS NULL")
                .contains("report_row.\"lifecycleStatus\" <> 'SUBMITTED'")
                .contains("report_row.\"recordedStudentCount\"");
        assertThat(query(missing).value())
                .contains("AS \"sectionCode\"")
                .contains("AS \"sectionName\"")
                .contains("AS \"attendanceSessionId\"")
                .contains("JOIN student_enrollments enrollment")
                .contains("enrollment.enrolled_on <=")
                .contains("enrollment.ended_on >=")
                .contains("attendance_record.academic_year_id =")
                .contains("attendance_record.grade_level_id =")
                .contains("attendance_record.section_id =");
        assertThat(query(missing).countQuery()).isBlank();
    }

    @Test
    void nativeAliasesMatchProjectionGetterNames() {
        assertAliases(query(uncheckedMethod("findDailySectionRecords",
                        Long.class, Long.class, Long.class, Long.class,
                        LocalDate.class)).value(),
                Set.of(
                        "attendanceRecordId",
                        "studentEnrollmentId",
                        "studentUserId",
                        "rollNumber",
                        "recordedStatus",
                        "effectiveStatus",
                        "earnedCredit",
                        "possibleCredit",
                        "latePenaltyApplied",
                        "remarks"
                ));
        assertAliases(query(uncheckedMethod("findMissingOrIncompleteSessions",
                        Long.class, Long.class, Long.class, Long.class,
                        LocalDate.class, LocalDate.class, Pageable.class))
                        .value(),
                Set.of(
                        "academicCalendarDayId",
                        "attendanceDate",
                        "gradeLevelId",
                        "sectionId",
                        "sectionCode",
                        "sectionName",
                        "attendanceSessionId",
                        "lifecycleStatus",
                        "eligibleStudentCount",
                        "recordedStudentCount"
                ));
    }

    private static void assertOfficialAggregateQuery(
            String methodName,
            Class<?>... parameterTypes
    ) throws Exception {
        Query query = query(method(methodName, parameterTypes));

        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value())
                .contains("attendance_record.organization_id =")
                .contains(":organizationId")
                .contains("attendance_record.effective_status")
                .contains("attendance_record.earned_credit")
                .contains("attendance_record.possible_credit")
                .contains("attendance_session.lifecycle_status =")
                .contains("'SUBMITTED'");
        assertFullRecordSessionJoin(query.value());
    }

    private static void assertFullRecordSessionJoin(String sql) {
        assertThat(sql)
                .contains("attendance_session.id =")
                .contains("attendance_record.attendance_session_id")
                .contains("attendance_session.organization_id =")
                .contains("attendance_record.organization_id")
                .contains("attendance_session.academic_year_id =")
                .contains("attendance_record.academic_year_id")
                .contains("attendance_session.grade_level_id =")
                .contains("attendance_record.grade_level_id")
                .contains("attendance_session.section_id =")
                .contains("attendance_record.section_id");
    }

    private static void assertAliases(
            String sql,
            Set<String> aliases
    ) {
        aliases.forEach(alias ->
                assertThat(sql).contains("AS \"" + alias + "\"")
        );
    }

    private static void assertParamNames(
            Method method,
            String... names
    ) {
        assertThat(Arrays.stream(method.getParameters())
                .filter(parameter ->
                        parameter.isAnnotationPresent(Param.class)
                )
                .map(parameter ->
                        parameter.getAnnotation(Param.class).value()
                )
                .toList())
                .containsExactly(names);
    }

    private static Query query(Method method) {
        return method.getAnnotation(Query.class);
    }

    private static Method method(
            String name,
            Class<?>... parameterTypes
    ) throws Exception {
        return StudentAttendanceReportRepository.class
                .getMethod(name, parameterTypes);
    }

    private static Method uncheckedMethod(
            String name,
            Class<?>... parameterTypes
    ) {
        try {
            return method(name, parameterTypes);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
