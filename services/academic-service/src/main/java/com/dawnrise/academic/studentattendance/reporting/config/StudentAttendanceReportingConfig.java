package com.dawnrise.academic.studentattendance.reporting.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(
        StudentAttendanceReportingProperties.class
)
public class StudentAttendanceReportingConfig {
}