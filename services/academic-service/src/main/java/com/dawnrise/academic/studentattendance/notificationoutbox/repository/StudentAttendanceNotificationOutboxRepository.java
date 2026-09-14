package com.dawnrise.academic.studentattendance.notificationoutbox.repository;

import com.dawnrise.academic.studentattendance.notificationoutbox.entity.StudentAttendanceNotificationOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentAttendanceNotificationOutboxRepository
        extends JpaRepository<
        StudentAttendanceNotificationOutboxEvent,
        Long
        > {

    boolean existsByOrganizationIdAndIdempotencyKey(
            Long organizationId,
            String idempotencyKey
    );

    Optional<StudentAttendanceNotificationOutboxEvent>
    findByEventId(UUID eventId);
}