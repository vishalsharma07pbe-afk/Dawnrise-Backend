package com.dawnrise.academic.timetable.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class TimetableGenerationExecutorConfiguration {
    @Bean("timetableGenerationExecutor")
    Executor timetableGenerationExecutor(){
        ThreadPoolTaskExecutor executor=new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);executor.setMaxPoolSize(2);executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("timetable-generator-");executor.initialize();return executor;
    }
}
