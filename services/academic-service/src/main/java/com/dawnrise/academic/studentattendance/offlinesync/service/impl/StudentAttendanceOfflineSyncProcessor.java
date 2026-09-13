package com.dawnrise.academic.studentattendance.offlinesync.service.impl;

import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncAppliedResult;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncRequest;
import com.dawnrise.academic.studentattendance.offlinesync.dto.StudentAttendanceOfflineSyncResponse;
import com.dawnrise.academic.studentattendance.offlinesync.entity.StudentAttendanceOfflineSyncOperation;
import com.dawnrise.academic.studentattendance.offlinesync.exception.StudentAttendanceOfflineSyncConflictException;
import com.dawnrise.academic.studentattendance.offlinesync.repository.StudentAttendanceOfflineSyncOperationRepository;
import com.dawnrise.academic.studentattendance.recording.service.StudentAttendanceRecordingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class StudentAttendanceOfflineSyncProcessor {

    private final StudentAttendanceOfflineSyncOperationRepository repository;
    private final StudentAttendanceRecordingService recordingService;
    private final ObjectMapper objectMapper;

    public StudentAttendanceOfflineSyncProcessor(
            StudentAttendanceOfflineSyncOperationRepository repository,
            StudentAttendanceRecordingService recordingService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.recordingService = recordingService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StudentAttendanceOfflineSyncResponse process(
            long organizationId,
            long actorUserId,
            long operationId,
            String idempotencyKey,
            String requestHash,
            StudentAttendanceOfflineSyncRequest request
    ) {
        StudentAttendanceOfflineSyncOperation operation = repository
                .findForUpdate(operationId, organizationId, actorUserId)
                .orElseThrow(() -> new StudentAttendanceOfflineSyncConflictException(
                        "Offline attendance synchronization operation was not found"
                ));
        StudentAttendanceOfflineSyncAppliedResult applied =
                recordingService.synchronizeOfflineDraft(
                        organizationId,
                        actorUserId,
                        request
                );
        StudentAttendanceOfflineSyncResponse response =
                new StudentAttendanceOfflineSyncResponse(
                        operationId,
                        idempotencyKey,
                        requestHash,
                        false,
                        applied.session(),
                        applied.appliedRecords()
                );
        try {
            operation.succeed(
                    applied.session().id(),
                    objectMapper.writeValueAsString(response)
            );
        } catch (StudentAttendanceOfflineSyncConflictException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new StudentAttendanceOfflineSyncConflictException(
                    "Offline attendance result could not be stored"
            );
        }
        repository.saveAndFlush(operation);
        return response;
    }
}

