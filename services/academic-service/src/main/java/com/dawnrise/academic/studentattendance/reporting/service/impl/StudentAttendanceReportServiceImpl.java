package com.dawnrise.academic.studentattendance.reporting.service.impl;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.common.integration.school.SchoolTimeZoneClient;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceSession;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSessionStatus;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceSessionRepository;
import com.dawnrise.academic.studentattendance.reporting.dto.*;
import com.dawnrise.academic.studentattendance.reporting.exception.InvalidStudentAttendanceReportException;
import com.dawnrise.academic.studentattendance.reporting.exception.StudentAttendanceReportNotFoundException;
import com.dawnrise.academic.studentattendance.reporting.mapper.StudentAttendanceReportMapper;
import com.dawnrise.academic.studentattendance.reporting.projection.*;
import com.dawnrise.academic.studentattendance.reporting.repository.StudentAttendanceReportRepository;
import com.dawnrise.academic.studentattendance.reporting.security.StudentAttendanceReportAccessService;
import com.dawnrise.academic.studentattendance.reporting.service.StudentAttendanceReportService;
import com.dawnrise.academic.studentattendance.reporting.validation.StudentAttendanceReportValidator;
import com.dawnrise.academic.studentenrollment.repository.StudentEnrollmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class StudentAttendanceReportServiceImpl
        implements StudentAttendanceReportService {

    private final StudentAttendanceReportRepository
            reportRepository;

    private final StudentAttendanceSessionRepository
            sessionRepository;

    private final StudentEnrollmentRepository
            enrollmentRepository;

    private final StudentAttendanceReportMapper mapper;

    private final StudentAttendanceReportValidator validator;

    private final StudentAttendanceReportAccessService
            accessService;

    private final SchoolTimeZoneClient schoolTimeZoneClient;

    private final Clock clock;

    public StudentAttendanceReportServiceImpl(
            StudentAttendanceReportRepository reportRepository,
            StudentAttendanceSessionRepository sessionRepository,
            StudentEnrollmentRepository enrollmentRepository,
            StudentAttendanceReportMapper mapper,
            StudentAttendanceReportValidator validator,
            StudentAttendanceReportAccessService accessService,
            SchoolTimeZoneClient schoolTimeZoneClient,
            Clock clock
    ) {
        this.reportRepository = reportRepository;
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.mapper = mapper;
        this.validator = validator;
        this.accessService = accessService;
        this.schoolTimeZoneClient = schoolTimeZoneClient;
        this.clock = clock;
    }

    @Override
    public DailySectionAttendanceReportResponse
    getDailySectionReport(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            LocalDate attendanceDate
    ) {
        AcademicYear academicYear =
                requireSectionContext(
                        organizationId,
                        actorUserId,
                        academicYearId,
                        gradeLevelId,
                        sectionId
                );

        validator.requireDateRange(
                academicYear,
                attendanceDate,
                attendanceDate
        );

        StudentAttendanceSession session =
                sessionRepository
                        .findByOrganizationIdAndAcademicYearIdAndSectionIdAndAttendanceDate(
                                organizationId,
                                academicYearId,
                                sectionId,
                                attendanceDate
                        )
                        .filter(candidate ->
                                candidate.getGradeLevelId()
                                        .equals(gradeLevelId)
                        )
                        .filter(candidate ->
                                candidate.getLifecycleStatus()
                                        == StudentAttendanceSessionStatus
                                        .SUBMITTED
                        )
                        .orElseThrow(() ->
                                new StudentAttendanceReportNotFoundException(
                                        "Submitted attendance was not found"
                                )
                        );

        List<DailyStudentAttendanceRowResponse> students =
                reportRepository
                        .findDailySectionRecords(
                                organizationId,
                                academicYearId,
                                gradeLevelId,
                                sectionId,
                                attendanceDate
                        )
                        .stream()
                        .map(mapper::toDailyStudent)
                        .toList();

        AttendanceSummary summary =
                mapper.toSummary(
                        reportRepository.summarizeSection(
                                organizationId,
                                academicYearId,
                                gradeLevelId,
                                sectionId,
                                attendanceDate,
                                attendanceDate
                        )
                );

        return new DailySectionAttendanceReportResponse(
                session.getId(),
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                session.getAcademicCalendarDayId(),
                attendanceDate,
                session.getLifecycleStatus(),
                session.getSubmissionType(),
                session.getSubmittedAt(),
                summary,
                students
        );
    }

    @Override
    public StudentAttendanceHistoryReportResponse
    getStudentHistory(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            long studentUserId,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        AcademicYear academicYear =
                requireStudentSectionContext(
                        organizationId,
                        actorUserId,
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        studentUserId
                );

        AttendanceReportDateRange dateRange =
                validator.requireDateRange(
                        academicYear,
                        fromDate,
                        toDate
                );

        Pageable safePageable =
                validator.sanitizePageable(pageable);

        Page<StudentAttendanceHistoryRowResponse> records =
                reportRepository
                        .findStudentHistory(
                                organizationId,
                                academicYearId,
                                studentUserId,
                                gradeLevelId,
                                sectionId,
                                dateRange.fromDate(),
                                dateRange.toDate(),
                                safePageable
                        )
                        .map(mapper::toHistoryRow);

        AttendanceSummary summary =
                mapper.toSummary(
                        reportRepository.summarizeStudent(
                                organizationId,
                                academicYearId,
                                studentUserId,
                                gradeLevelId,
                                sectionId,
                                dateRange.fromDate(),
                                dateRange.toDate()
                        )
                );

        return new StudentAttendanceHistoryReportResponse(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                studentUserId,
                dateRange,
                summary,
                AttendancePageResponse.from(records)
        );
    }

    @Override
    public MonthlyStudentAttendanceReportResponse
    getStudentMonthlySummary(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            long studentUserId,
            YearMonth month
    ) {
        AcademicYear academicYear =
                requireStudentSectionContext(
                        organizationId,
                        actorUserId,
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        studentUserId
                );

        AttendanceReportDateRange monthRange =
                validator.requireMonthRange(
                        academicYear,
                        month
                );

        StudentMonthlyAttendanceProjection projection =
                reportRepository.summarizeStudentMonth(
                        organizationId,
                        academicYearId,
                        studentUserId,
                        gradeLevelId,
                        sectionId,
                        monthRange.fromDate(),
                        monthRange.toDate()
                );

        long submittedDays =
                projection == null
                        || projection.getSubmittedAttendanceDays()
                        == null
                        ? 0L
                        : projection.getSubmittedAttendanceDays();

        return new MonthlyStudentAttendanceReportResponse(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                studentUserId,
                month,
                submittedDays,
                mapper.toSummary(projection)
        );
    }

    @Override
    public SectionAttendanceSummaryResponse getSectionSummary(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        AcademicYear academicYear =
                requireSectionContext(
                        organizationId,
                        actorUserId,
                        academicYearId,
                        gradeLevelId,
                        sectionId
                );

        AttendanceReportDateRange dateRange =
                validator.requireDateRange(
                        academicYear,
                        fromDate,
                        toDate
                );

        AttendanceAggregateProjection projection =
                reportRepository.summarizeSection(
                        organizationId,
                        academicYearId,
                        gradeLevelId,
                        sectionId,
                        dateRange.fromDate(),
                        dateRange.toDate()
                );

        return new SectionAttendanceSummaryResponse(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                dateRange,
                submittedSessionCount(projection),
                distinctStudentCount(projection),
                mapper.toSummary(projection)
        );
    }

    @Override
    public GradeAttendanceSummaryResponse getGradeSummary(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        accessService.requireLeadershipAccess();

        AcademicYear academicYear =
                validator.requireAcademicYear(
                        organizationId,
                        academicYearId
                );

        validator.requireGradeLevel(
                organizationId,
                academicYearId,
                gradeLevelId
        );

        AttendanceReportDateRange dateRange =
                validator.requireDateRange(
                        academicYear,
                        fromDate,
                        toDate
                );

        AttendanceSummary summary =
                mapper.toSummary(
                        reportRepository.summarizeGrade(
                                organizationId,
                                academicYearId,
                                gradeLevelId,
                                dateRange.fromDate(),
                                dateRange.toDate()
                        )
                );

        List<GradeAttendanceSectionRowResponse> sections =
                reportRepository
                        .summarizeGradeSections(
                                organizationId,
                                academicYearId,
                                gradeLevelId,
                                dateRange.fromDate(),
                                dateRange.toDate()
                        )
                        .stream()
                        .map(mapper::toGradeSection)
                        .toList();

        return new GradeAttendanceSummaryResponse(
                organizationId,
                academicYearId,
                gradeLevelId,
                dateRange,
                summary,
                sections
        );
    }

    @Override
    public LowAttendanceReportResponse
    getLowAttendanceStudents(
            long organizationId,
            long actorUserId,
            long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal thresholdPercentage,
            Pageable pageable
    ) {
        AcademicYear academicYear =
                requireOptionalScopeContext(
                        organizationId,
                        actorUserId,
                        academicYearId,
                        gradeLevelId,
                        sectionId
                );

        AttendanceReportDateRange dateRange =
                validator.requireDateRange(
                        academicYear,
                        fromDate,
                        toDate
                );

        BigDecimal safeThreshold =
                validator.requireThresholdPercentage(
                        thresholdPercentage
                );

        Pageable safePageable =
                validator.sanitizePageable(pageable);

        Page<LowAttendanceStudentResponse> students =
                reportRepository
                        .findLowAttendanceStudents(
                                organizationId,
                                academicYearId,
                                gradeLevelId,
                                sectionId,
                                dateRange.fromDate(),
                                dateRange.toDate(),
                                safeThreshold,
                                safePageable
                        )
                        .map(mapper::toLowAttendanceStudent);

        return new LowAttendanceReportResponse(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                dateRange,
                safeThreshold,
                AttendancePageResponse.from(students)
        );
    }

    @Override
    public MissingAttendanceReportResponse getMissingAttendance(
            long organizationId,
            long actorUserId,
            long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        AcademicYear academicYear =
                requireOptionalScopeContext(
                        organizationId,
                        actorUserId,
                        academicYearId,
                        gradeLevelId,
                        sectionId
                );

        AttendanceReportDateRange dateRange =
                validator.requireDateRange(
                        academicYear,
                        fromDate,
                        toDate
                );

        rejectFutureMissingAttendanceRange(
                organizationId,
                dateRange.toDate()
        );

        Pageable safePageable =
                validator.sanitizePageable(pageable);

        Slice<MissingAttendanceSessionProjection> result =
                reportRepository
                        .findMissingOrIncompleteSessions(
                                organizationId,
                                academicYearId,
                                gradeLevelId,
                                sectionId,
                                dateRange.fromDate(),
                                dateRange.toDate(),
                                safePageable
                        );

        List<MissingAttendanceSessionResponse> items =
                result.getContent()
                        .stream()
                        .map(mapper::toMissingSession)
                        .toList();

        return new MissingAttendanceReportResponse(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                dateRange,
                result.getNumber(),
                result.getSize(),
                result.hasNext(),
                items
        );
    }

    private AcademicYear requireSectionContext(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId
    ) {
        AcademicYear academicYear =
                validator.requireAcademicYear(
                        organizationId,
                        academicYearId
                );

        validator.requireGradeLevel(
                organizationId,
                academicYearId,
                gradeLevelId
        );

        validator.requireSection(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId
        );

        accessService.requireSectionAccess(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId,
                actorUserId
        );

        return academicYear;
    }

    private AcademicYear requireStudentSectionContext(
            long organizationId,
            long actorUserId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            long studentUserId
    ) {
        if (studentUserId <= 0) {
            throw new InvalidStudentAttendanceReportException(
                    "Student user ID must be positive"
            );
        }

        AcademicYear academicYear =
                requireSectionContext(
                        organizationId,
                        actorUserId,
                        academicYearId,
                        gradeLevelId,
                        sectionId
                );

        boolean enrollmentExists =
                enrollmentRepository
                        .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndStudentUserId(
                                organizationId,
                                academicYearId,
                                gradeLevelId,
                                sectionId,
                                studentUserId
                        );

        if (!enrollmentExists) {
            throw new StudentAttendanceReportNotFoundException(
                    "Student enrollment was not found"
            );
        }

        return academicYear;
    }

    private AcademicYear requireOptionalScopeContext(
            long organizationId,
            long actorUserId,
            long academicYearId,
            Long gradeLevelId,
            Long sectionId
    ) {
        AcademicYear academicYear =
                validator.requireAcademicYear(
                        organizationId,
                        academicYearId
                );

        validator.requireOptionalScope(
                organizationId,
                academicYearId,
                gradeLevelId,
                sectionId
        );

        if (sectionId == null) {
            accessService.requireLeadershipAccess();
        } else {
            accessService.requireSectionAccess(
                    organizationId,
                    academicYearId,
                    gradeLevelId,
                    sectionId,
                    actorUserId
            );
        }

        return academicYear;
    }

    private long submittedSessionCount(
            AttendanceAggregateProjection projection
    ) {
        return projection == null
                || projection.getSubmittedSessionCount() == null
                ? 0L
                : projection.getSubmittedSessionCount();
    }

    private long distinctStudentCount(
            AttendanceAggregateProjection projection
    ) {
        return projection == null
                || projection.getDistinctStudentCount() == null
                ? 0L
                : projection.getDistinctStudentCount();
    }

    private void rejectFutureMissingAttendanceRange(
            long organizationId,
            LocalDate toDate
    ) {
        ZoneId schoolZone = schoolTimeZoneClient.getTimeZone(organizationId);
        LocalDate schoolToday = LocalDate.now(clock.withZone(schoolZone));
        if (toDate.isAfter(schoolToday)) {
            throw new InvalidStudentAttendanceReportException(
                    "Missing attendance report cannot include future dates"
            );
        }
    }
}
