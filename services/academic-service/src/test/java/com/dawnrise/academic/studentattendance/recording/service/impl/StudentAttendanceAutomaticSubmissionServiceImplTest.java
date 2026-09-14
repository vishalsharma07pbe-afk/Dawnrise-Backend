package com.dawnrise.academic.studentattendance.recording.service.impl;

import com.dawnrise.academic.common.integration.school.SchoolTimeZoneClient;
import com.dawnrise.academic.common.integration.school.SchoolTimeZoneUnavailableException;
import com.dawnrise.academic.studentattendance.config.StudentAttendanceProperties;
import com.dawnrise.academic.studentattendance.notificationoutbox.service.StudentAttendanceNotificationOutboxService;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StudentAttendanceAutomaticSubmissionServiceImplTest {

    @Test
    void candidateDiscoveryIsBoundedAndProcessedInRepositoryOrder() {
        SessionRepositoryStub sessionRepository = new SessionRepositoryStub(
                List.of(candidate(1L, 10L), candidate(2L, 10L))
        );
        ProcessorStub processor = new ProcessorStub(List.of(1L));
        TimeZoneClientStub timeZoneClient = new TimeZoneClientStub();
        timeZoneClient.organization10Zone = ZoneId.of("Asia/Kolkata");

        int submitted = service(sessionRepository, processor, timeZoneClient, 2)
                .processEligibleDrafts();

        assertThat(submitted).isEqualTo(1);
        assertThat(sessionRepository.pageSize).isEqualTo(2);
        assertThat(processor.processedIds).containsExactly(1L, 2L);
        assertThat(timeZoneClient.organization10Calls).isEqualTo(1);
    }

    @Test
    void timezoneFailureForOneOrganizationDoesNotBlockAnother() {
        SessionRepositoryStub sessionRepository = new SessionRepositoryStub(
                List.of(candidate(1L, 10L), candidate(2L, 20L), candidate(3L, 10L))
        );
        ProcessorStub processor = new ProcessorStub(List.of(2L));
        TimeZoneClientStub timeZoneClient = new TimeZoneClientStub();
        timeZoneClient.organization10Fails = true;
        timeZoneClient.organization20Zone = ZoneId.of("Asia/Kolkata");

        int submitted = service(sessionRepository, processor, timeZoneClient, 100)
                .processEligibleDrafts();

        assertThat(submitted).isEqualTo(1);
        assertThat(processor.processedIds).containsExactly(2L);
        assertThat(timeZoneClient.organization10Calls).isEqualTo(1);
        assertThat(timeZoneClient.organization20Calls).isEqualTo(1);
    }

    private StudentAttendanceAutomaticSubmissionServiceImpl service(
            SessionRepositoryStub sessionRepository,
            ProcessorStub processor,
            TimeZoneClientStub timeZoneClient,
            int batchSize
    ) {
        return new StudentAttendanceAutomaticSubmissionServiceImpl(
                sessionRepository.repository(),
                processor,
                timeZoneClient,
                properties(batchSize),
                Clock.fixed(Instant.parse("2026-09-12T06:00:00Z"), ZoneId.of("UTC"))
        );
    }

    private static StudentAttendanceProperties properties(int batchSize) {
        StudentAttendanceProperties properties = new StudentAttendanceProperties();
        properties.setAutomaticSubmissionCandidateBatchSize(batchSize);
        return properties;
    }

    private static StudentAttendanceSessionRepository.AutomaticSubmissionCandidate candidate(
            Long id,
            Long organizationId
    ) {
        return new StudentAttendanceSessionRepository.AutomaticSubmissionCandidate() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Long getOrganizationId() {
                return organizationId;
            }
        };
    }

    private static final class SessionRepositoryStub {
        private final List<StudentAttendanceSessionRepository.AutomaticSubmissionCandidate> candidates;
        private int pageSize;

        private SessionRepositoryStub(List<StudentAttendanceSessionRepository.AutomaticSubmissionCandidate> candidates) {
            this.candidates = candidates;
        }

        private StudentAttendanceSessionRepository repository() {
            return proxy(StudentAttendanceSessionRepository.class, (proxy, method, args) -> {
                if (method.getName().equals("findAutomaticSubmissionCandidates")) {
                    pageSize = ((Pageable) args[1]).getPageSize();
                    return candidates;
                }
                return defaultObjectMethod(proxy, method.getName(), args);
            });
        }
    }

    private static class ProcessorStub extends StudentAttendanceAutomaticSubmissionSessionProcessor {
        private final List<Long> submittedIds;
        private final List<Long> processedIds = new ArrayList<>();

        private ProcessorStub(List<Long> submittedIds) {
            super(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    mock(StudentAttendanceNotificationOutboxService.class)
            );
            this.submittedIds = submittedIds;
        }

        @Override
        public boolean processCandidate(long sessionId, LocalDate schoolToday, OffsetDateTime now) {
            processedIds.add(sessionId);
            return submittedIds.contains(sessionId);
        }
    }

    private static final class TimeZoneClientStub implements SchoolTimeZoneClient {
        private ZoneId organization10Zone;
        private ZoneId organization20Zone;
        private boolean organization10Fails;
        private int organization10Calls;
        private int organization20Calls;

        @Override
        public ZoneId getTimeZone(long organizationId) {
            if (organizationId == 10L) {
                organization10Calls++;
                if (organization10Fails) {
                    throw new SchoolTimeZoneUnavailableException("unavailable");
                }
                return organization10Zone;
            }
            if (organizationId == 20L) {
                organization20Calls++;
                return organization20Zone;
            }
            throw new IllegalArgumentException("Unexpected organization");
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler
        ));
    }

    private static Object defaultObjectMethod(Object proxy, String methodName, Object[] args) {
        if (methodName.equals("hashCode")) {
            return System.identityHashCode(proxy);
        }
        if (methodName.equals("equals")) {
            return proxy == args[0];
        }
        if (methodName.equals("toString")) {
            return "RepositoryStub";
        }
        throw new UnsupportedOperationException(methodName);
    }
}
