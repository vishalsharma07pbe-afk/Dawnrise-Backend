package com.dawnrise.academic.studentattendance.recording.service.impl;

import com.dawnrise.academic.academiccalendar.enums.AttendanceRequirement;
import com.dawnrise.academic.academiccalendar.repository.AcademicCalendarDayRepository;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendancePolicyRepository;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceRecord;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSessionStatus;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceRecordRepository;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceSessionRepository;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentenrollment.repository.StudentEnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.dawnrise.academic.studentattendance.notificationoutbox.service.StudentAttendanceNotificationOutboxService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Component
class StudentAttendanceAutomaticSubmissionSessionProcessor {

    private static final Logger log = LoggerFactory.getLogger(
            StudentAttendanceAutomaticSubmissionSessionProcessor.class
    );

    private final StudentAttendanceSessionRepository sessionRepository;
    private final StudentAttendanceRecordRepository recordRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AcademicCalendarDayRepository calendarDayRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final StudentAttendancePolicyRepository policyRepository;
    private final StudentAttendanceNotificationOutboxService notificationOutboxService;

    StudentAttendanceAutomaticSubmissionSessionProcessor(
            StudentAttendanceSessionRepository sessionRepository,
            StudentAttendanceRecordRepository recordRepository,
            AcademicYearRepository academicYearRepository,
            AcademicCalendarDayRepository calendarDayRepository,
            StudentEnrollmentRepository enrollmentRepository,
            StudentAttendancePolicyRepository policyRepository,
            StudentAttendanceNotificationOutboxService
                    notificationOutboxService
    ) {
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.academicYearRepository = academicYearRepository;
        this.calendarDayRepository = calendarDayRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.policyRepository = policyRepository;
        this.notificationOutboxService =
                notificationOutboxService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processCandidate(
            long sessionId,
            LocalDate schoolToday,
            OffsetDateTime now
    ) {
        var session = sessionRepository
                .findByIdForAutomaticSubmissionUpdate(sessionId)
                .orElse(null);
        if (session == null
                || session.getLifecycleStatus() != StudentAttendanceSessionStatus.DRAFT) {
            return false;
        }
        var policy = policyRepository.findById(session.getOrganizationId()).orElse(null);
        if (policy == null || !Boolean.TRUE.equals(policy.getAutomaticSubmissionEnabled())) {
            return false;
        }
        if (session.getUpdatedAt() == null
                || session.getUpdatedAt()
                .plusMinutes(policy.getAutomaticSubmissionMinutes())
                .isAfter(now)) {
            return false;
        }
        if (session.getAttendanceDate().isAfter(schoolToday)) {
            return false;
        }
        var year = academicYearRepository
                .findByIdAndOrganizationId(
                        session.getAcademicYearId(),
                        session.getOrganizationId()
                )
                .orElse(null);
        if (year == null || year.getStatus() != AcademicYearStatus.ACTIVE) {
            return false;
        }
        var calendarDay = calendarDayRepository
                .findByOrganizationIdAndAcademicYearIdAndCalendarDate(
                        session.getOrganizationId(),
                        session.getAcademicYearId(),
                        session.getAttendanceDate()
                )
                .orElse(null);
        if (calendarDay == null
                || calendarDay.getAttendanceRequirement() == AttendanceRequirement.NOT_APPLICABLE
                || !calendarDay.isCountsTowardPercentage()) {
            return false;
        }

        var required = enrollmentRepository.findEligibleForAttendanceDate(
                session.getOrganizationId(),
                session.getAcademicYearId(),
                session.getGradeLevelId(),
                session.getSectionId(),
                session.getAttendanceDate()
        );
        if (required.isEmpty()) {
            return false;
        }
        var records = recordRepository.findAllByAttendanceSessionIdOrderByIdAsc(session.getId());
        if (records.isEmpty()) {
            return false;
        }
        Set<Long> requiredEnrollmentIds = required.stream()
                .map(StudentEnrollment::getId)
                .collect(Collectors.toSet());
        Set<Long> markedEnrollmentIds = records.stream()
                .map(StudentAttendanceRecord::getStudentEnrollmentId)
                .collect(Collectors.toSet());
        if (!markedEnrollmentIds.equals(requiredEnrollmentIds)) {
            return false;
        }

        session.submitAutomatically(now);
        notificationOutboxService.createForSubmittedSession(
                session,
                records
        );

        log.info(
                "Automatically submitted student attendance session organizationId={} academicYearId={} sectionId={} attendanceDate={} sessionId={}",
                session.getOrganizationId(),
                session.getAcademicYearId(),
                session.getSectionId(),
                session.getAttendanceDate(),
                session.getId()
        );
        return true;
    }
}
