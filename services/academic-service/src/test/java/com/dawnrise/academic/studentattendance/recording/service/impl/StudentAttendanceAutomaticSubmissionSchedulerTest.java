package com.dawnrise.academic.studentattendance.recording.service.impl;

import com.dawnrise.academic.studentattendance.recording.service.StudentAttendanceAutomaticSubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceAutomaticSubmissionSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(StudentAttendanceAutomaticSubmissionService.class,
                    () -> () -> 0)
            .withUserConfiguration(SchedulerConfig.class);

    @Test
    void schedulerBeanIsDisabledByDefault() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(StudentAttendanceAutomaticSubmissionScheduler.class));
    }

    @Test
    void schedulerBeanIsCreatedWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues(
                        "dawnrise.student-attendance.automatic-submission-scheduler-enabled=true"
                )
                .run(context -> assertThat(context)
                        .hasSingleBean(StudentAttendanceAutomaticSubmissionScheduler.class));
    }

    @Configuration
    @Import(StudentAttendanceAutomaticSubmissionScheduler.class)
    static class SchedulerConfig {
    }
}
