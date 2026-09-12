package com.dawnrise.academic.studentattendance.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StudentAttendanceProperties.class)
public class StudentAttendanceConfiguration {
}
