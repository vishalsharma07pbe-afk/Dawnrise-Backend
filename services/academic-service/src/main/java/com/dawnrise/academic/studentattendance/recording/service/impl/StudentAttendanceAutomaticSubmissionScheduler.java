package com.dawnrise.academic.studentattendance.recording.service.impl;

import com.dawnrise.academic.studentattendance.recording.service.StudentAttendanceAutomaticSubmissionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "dawnrise.student-attendance",
        name = "automatic-submission-scheduler-enabled",
        havingValue = "true"
)
class StudentAttendanceAutomaticSubmissionScheduler {

    private final StudentAttendanceAutomaticSubmissionService service;

    StudentAttendanceAutomaticSubmissionScheduler(StudentAttendanceAutomaticSubmissionService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${dawnrise.student-attendance.automatic-submission-scheduler-delay:PT5M}",
            initialDelayString = "${dawnrise.student-attendance.automatic-submission-scheduler-initial-delay:PT5M}"
    )
    void processEligibleDrafts() {
        service.processEligibleDrafts();
    }
}
