package com.dawnrise.academic.studentattendance.recording.service.impl;

import com.dawnrise.academic.common.integration.school.SchoolTimeZoneClient;
import com.dawnrise.academic.common.integration.school.SchoolTimeZoneUnavailableException;
import com.dawnrise.academic.studentattendance.config.StudentAttendanceProperties;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceSessionRepository;
import com.dawnrise.academic.studentattendance.recording.service.StudentAttendanceAutomaticSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StudentAttendanceAutomaticSubmissionServiceImpl
        implements StudentAttendanceAutomaticSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(
            StudentAttendanceAutomaticSubmissionServiceImpl.class
    );

    private final StudentAttendanceSessionRepository sessionRepository;
    private final StudentAttendanceAutomaticSubmissionSessionProcessor processor;
    private final SchoolTimeZoneClient schoolTimeZoneClient;
    private final StudentAttendanceProperties properties;
    private final Clock clock;

    public StudentAttendanceAutomaticSubmissionServiceImpl(
            StudentAttendanceSessionRepository sessionRepository,
            StudentAttendanceAutomaticSubmissionSessionProcessor processor,
            SchoolTimeZoneClient schoolTimeZoneClient,
            StudentAttendanceProperties properties,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.processor = processor;
        this.schoolTimeZoneClient = schoolTimeZoneClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public int processEligibleDrafts() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<StudentAttendanceSessionRepository.AutomaticSubmissionCandidate> candidates =
                sessionRepository.findAutomaticSubmissionCandidates(
                        now,
                        PageRequest.of(0, properties.getAutomaticSubmissionCandidateBatchSize())
                );
        Set<Long> failedOrganizations = new java.util.HashSet<>();
        Map<Long, ZoneId> organizationZones = new HashMap<>();
        int submitted = 0;
        for (StudentAttendanceSessionRepository.AutomaticSubmissionCandidate candidate : candidates) {
            Long organizationId = candidate.getOrganizationId();
            if (failedOrganizations.contains(organizationId)) {
                continue;
            }
            ZoneId schoolZone = organizationZones.get(organizationId);
            if (schoolZone == null) {
                try {
                    schoolZone = schoolTimeZoneClient.getTimeZone(organizationId);
                    organizationZones.put(organizationId, schoolZone);
                } catch (SchoolTimeZoneUnavailableException exception) {
                    failedOrganizations.add(organizationId);
                    log.warn(
                            "Skipping automatic student attendance submissions because school timezone is unavailable organizationId={}",
                            organizationId
                    );
                    continue;
                }
            }
            LocalDate schoolToday = LocalDate.now(clock.withZone(schoolZone));
            if (processor.processCandidate(candidate.getId(), schoolToday, now)) {
                submitted++;
            }
        }
        return submitted;
    }
}
