package com.dawnrise.academic.studentattendance.notificationoutbox.service.impl;

import com.dawnrise.academic.studentattendance.correction.entity.StudentAttendanceCorrectionItem;
import com.dawnrise.academic.studentattendance.notificationoutbox.entity.StudentAttendanceNotificationOutboxEvent;
import com.dawnrise.academic.studentattendance.notificationoutbox.enums.StudentAttendanceNotificationEventSource;
import com.dawnrise.academic.studentattendance.notificationoutbox.enums.StudentAttendanceNotificationEventType;
import com.dawnrise.academic.studentattendance.notificationoutbox.payload.StudentAttendanceNotificationPayload;
import com.dawnrise.academic.studentattendance.notificationoutbox.repository.StudentAttendanceNotificationOutboxRepository;
import com.dawnrise.academic.studentattendance.notificationoutbox.service.StudentAttendanceNotificationClassifier;
import com.dawnrise.academic.studentattendance.notificationoutbox.service.StudentAttendanceNotificationOutboxService;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceRecord;
import com.dawnrise.academic.studentattendance.recording.entity.StudentAttendanceSession;
import com.dawnrise.academic.studentattendance.recording.enums.StudentAttendanceSubmissionType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class StudentAttendanceNotificationOutboxServiceImpl
        implements StudentAttendanceNotificationOutboxService {

    private final StudentAttendanceNotificationOutboxRepository repository;
    private final StudentAttendanceNotificationClassifier classifier;
    private final ObjectMapper objectMapper;

    public StudentAttendanceNotificationOutboxServiceImpl(
            StudentAttendanceNotificationOutboxRepository repository,
            StudentAttendanceNotificationClassifier classifier,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.classifier = classifier;
        this.objectMapper = objectMapper;
    }

    @Override
    public void createForSubmittedSession(
            StudentAttendanceSession session,
            List<StudentAttendanceRecord> records
    ) {
        requireSubmittedSession(session);

        if (records == null) {
            throw new IllegalArgumentException(
                    "Attendance records are required"
            );
        }

        StudentAttendanceNotificationEventSource eventSource =
                submissionSource(session.getSubmissionType());

        for (StudentAttendanceRecord record : records) {
            StudentAttendanceNotificationEventType eventType =
                    classifier.classify(
                            record.getRecordedStatus(),
                            record.getEffectiveStatus(),
                            record.isLatePenaltyApplied()
                    ).orElse(null);

            if (eventType == null) {
                continue;
            }

            String idempotencyKey = String.format(
                    "student-attendance:submission:%d:record:%d:event:%s",
                    session.getId(),
                    record.getId(),
                    eventType.name()
            );

            createEvent(
                    session,
                    record,
                    null,
                    eventType,
                    eventSource,
                    null,
                    null,
                    null,
                    idempotencyKey,
                    session.getSubmittedAt()
            );
        }
    }

    @Override
    public void createForApprovedCorrection(
            StudentAttendanceSession session,
            Long correctionRequestId,
            List<StudentAttendanceCorrectionItem> correctionItems,
            Map<Long, StudentAttendanceRecord> correctedRecords,
            OffsetDateTime occurredAt
    ) {
        requireSubmittedSession(session);
        requirePositive(
                correctionRequestId,
                "Correction request ID"
        );

        if (correctionItems == null) {
            throw new IllegalArgumentException(
                    "Correction items are required"
            );
        }
        if (correctedRecords == null) {
            throw new IllegalArgumentException(
                    "Corrected attendance records are required"
            );
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException(
                    "Occurred timestamp is required"
            );
        }

        for (StudentAttendanceCorrectionItem item : correctionItems) {
            StudentAttendanceRecord correctedRecord =
                    correctedRecords.get(
                            item.getAttendanceRecordId()
                    );

            if (correctedRecord == null) {
                throw new IllegalArgumentException(
                        "Corrected attendance record is missing"
                );
            }

            if (!classifier.notificationSnapshotChanged(
                    item.getPreviousRecordedStatus(),
                    item.getPreviousEffectiveStatus(),
                    item.isPreviousLatePenaltyApplied(),
                    correctedRecord.getRecordedStatus(),
                    correctedRecord.getEffectiveStatus(),
                    correctedRecord.isLatePenaltyApplied()
            )) {
                continue;
            }

            Optional<StudentAttendanceNotificationEventType>
                    previousType = classifier.classify(
                    item.getPreviousRecordedStatus(),
                    item.getPreviousEffectiveStatus(),
                    item.isPreviousLatePenaltyApplied()
            );

            Optional<StudentAttendanceNotificationEventType>
                    currentType = classifier.classify(
                    correctedRecord.getRecordedStatus(),
                    correctedRecord.getEffectiveStatus(),
                    correctedRecord.isLatePenaltyApplied()
            );

            StudentAttendanceNotificationEventType eventType;

            if (currentType.isPresent()) {
                eventType = currentType.get();
            } else if (previousType.isPresent()) {
                eventType =
                        StudentAttendanceNotificationEventType
                                .ATTENDANCE_ALERT_CLEARED;
            } else {
                continue;
            }

            String idempotencyKey = String.format(
                    "student-attendance:correction:%d:record:%d:event:%s",
                    correctionRequestId,
                    correctedRecord.getId(),
                    eventType.name()
            );

            createEvent(
                    session,
                    correctedRecord,
                    correctionRequestId,
                    eventType,
                    StudentAttendanceNotificationEventSource
                            .CORRECTION_APPROVAL,
                    item.getPreviousRecordedStatus(),
                    item.getPreviousEffectiveStatus(),
                    item.isPreviousLatePenaltyApplied(),
                    idempotencyKey,
                    occurredAt
            );
        }
    }

    private void createEvent(
            StudentAttendanceSession session,
            StudentAttendanceRecord record,
            Long correctionRequestId,
            StudentAttendanceNotificationEventType eventType,
            StudentAttendanceNotificationEventSource eventSource,
            AttendanceStatus previousRecordedStatus,
            AttendanceStatus previousEffectiveStatus,
            Boolean previousLatePenaltyApplied,
            String idempotencyKey,
            OffsetDateTime occurredAt
    ) {
        validateRecordContext(session, record);

        if (repository
                .existsByOrganizationIdAndIdempotencyKey(
                        session.getOrganizationId(),
                        idempotencyKey
                )) {
            return;
        }

        StudentAttendanceNotificationPayload payload =
                new StudentAttendanceNotificationPayload(
                        1,
                        eventType,
                        eventSource,
                        session.getOrganizationId(),
                        session.getAcademicYearId(),
                        session.getGradeLevelId(),
                        session.getSectionId(),
                        session.getId(),
                        record.getId(),
                        correctionRequestId,
                        record.getStudentEnrollmentId(),
                        record.getStudentUserId(),
                        session.getAttendanceDate(),
                        previousRecordedStatus,
                        previousEffectiveStatus,
                        previousLatePenaltyApplied,
                        record.getRecordedStatus(),
                        record.getEffectiveStatus(),
                        record.getEarnedCredit(),
                        record.getPossibleCredit(),
                        record.isLatePenaltyApplied(),
                        session.getSubmissionType(),
                        occurredAt
                );

        repository.save(
                new StudentAttendanceNotificationOutboxEvent(
                        UUID.randomUUID(),
                        session.getOrganizationId(),
                        session.getAcademicYearId(),
                        session.getGradeLevelId(),
                        session.getSectionId(),
                        session.getId(),
                        record.getId(),
                        correctionRequestId,
                        record.getStudentEnrollmentId(),
                        record.getStudentUserId(),
                        session.getAttendanceDate(),
                        eventType,
                        eventSource,
                        idempotencyKey,
                        serialize(payload),
                        occurredAt
                )
        );
    }

    private String serialize(
            StudentAttendanceNotificationPayload payload
    ) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Attendance notification event "
                            + "could not be serialized",
                    exception
            );
        }
    }

    private StudentAttendanceNotificationEventSource
    submissionSource(
            StudentAttendanceSubmissionType submissionType
    ) {
        if (submissionType
                == StudentAttendanceSubmissionType.MANUAL) {
            return StudentAttendanceNotificationEventSource
                    .MANUAL_SUBMISSION;
        }

        if (submissionType
                == StudentAttendanceSubmissionType.AUTOMATIC) {
            return StudentAttendanceNotificationEventSource
                    .AUTOMATIC_SUBMISSION;
        }

        throw new IllegalArgumentException(
                "Attendance submission type is invalid"
        );
    }

    private void requireSubmittedSession(
            StudentAttendanceSession session
    ) {
        if (session == null) {
            throw new IllegalArgumentException(
                    "Attendance session is required"
            );
        }
        if (session.getId() == null) {
            throw new IllegalArgumentException(
                    "Persisted attendance session is required"
            );
        }
        if (session.getSubmittedAt() == null
                || session.getSubmissionType() == null) {
            throw new IllegalArgumentException(
                    "Submitted attendance session is required"
            );
        }
    }

    private void validateRecordContext(
            StudentAttendanceSession session,
            StudentAttendanceRecord record
    ) {
        if (record == null || record.getId() == null) {
            throw new IllegalArgumentException(
                    "Persisted attendance record is required"
            );
        }

        if (!session.getOrganizationId().equals(
                record.getOrganizationId()
        )
                || !session.getAcademicYearId().equals(
                record.getAcademicYearId()
        )
                || !session.getGradeLevelId().equals(
                record.getGradeLevelId()
        )
                || !session.getSectionId().equals(
                record.getSectionId()
        )
                || !session.getId().equals(
                record.getAttendanceSessionId()
        )) {
            throw new IllegalArgumentException(
                    "Attendance record does not belong "
                            + "to the submitted session"
            );
        }
    }

    private void requirePositive(
            Long value,
            String fieldName
    ) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }
    }
}