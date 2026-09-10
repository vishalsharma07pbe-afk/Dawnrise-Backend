package com.dawnrise.academic.studentprogression.service.impl;

import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentenrollment.repository.StudentEnrollmentRepository;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationRequest;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionConfirmationResponse;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionCountsResponse;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionItemResultResponse;
import com.dawnrise.academic.studentprogression.entity.StudentProgressionItem;
import com.dawnrise.academic.studentprogression.entity.StudentProgressionOperation;
import com.dawnrise.academic.studentprogression.enums.StudentProgressionOperationStatus;
import com.dawnrise.academic.studentprogression.enums.StudentProgressionOutcome;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionConflictException;
import com.dawnrise.academic.studentprogression.exception.StudentProgressionOperationNotFoundException;
import com.dawnrise.academic.studentprogression.repository.StudentProgressionItemRepository;
import com.dawnrise.academic.studentprogression.repository.StudentProgressionOperationRepository;
import com.dawnrise.academic.studentprogression.service.StudentProgressionExecutor;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlan;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlanBuilder;
import com.dawnrise.academic.studentprogression.service.StudentProgressionPlanItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class StudentProgressionExecutorImpl
        implements StudentProgressionExecutor {

    private final StudentProgressionOperationRepository operationRepository;
    private final StudentProgressionItemRepository itemRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final StudentProgressionPlanBuilder planBuilder;
    private final ObjectMapper objectMapper;

    public StudentProgressionExecutorImpl(
            StudentProgressionOperationRepository operationRepository,
            StudentProgressionItemRepository itemRepository,
            StudentEnrollmentRepository enrollmentRepository,
            StudentProgressionPlanBuilder planBuilder,
            ObjectMapper objectMapper
    ) {
        this.operationRepository = operationRepository;
        this.itemRepository = itemRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.planBuilder = planBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public StudentProgressionConfirmationResponse execute(
            long organizationId,
            long operationId,
            long targetAcademicYearId,
            String requestHash,
            StudentProgressionConfirmationRequest request
    ) {
        StudentProgressionOperation operation =
                operationRepository
                        .findByIdAndOrganizationIdForUpdate(
                                operationId,
                                organizationId
                        )
                        .orElseThrow(() ->
                                new StudentProgressionOperationNotFoundException(
                                        "Student progression operation not found"
                                )
                        );

        validateOperation(
                operation,
                requestHash,
                targetAcademicYearId,
                request
        );

        StudentProgressionPlan plan =
                planBuilder.buildForConfirmation(
                        organizationId,
                        targetAcademicYearId,
                        request
                );

        if (!Objects.equals(
                plan.previewFingerprint(),
                request.previewFingerprint()
        )) {
            throw new StudentProgressionConflictException(
                    "Student progression preview is stale",
                    StudentProgressionOperationStatus.STALE_PREVIEW,
                    "STALE_PREVIEW"
            );
        }

        if (!plan.canConfirm()) {
            throw new StudentProgressionConflictException(
                    "Student progression contains blocking conflicts",
                    StudentProgressionOperationStatus.CONFLICTED,
                    "PROGRESSION_CONFLICTS"
            );
        }

        Map<Long, StudentEnrollment> targetBySourceId =
                createTargetEnrollments(
                        organizationId,
                        plan
                );

        updateSourceEnrollments(plan);

        /*
         * Flush enrollment mutations before progression items so every
         * target enrollment has its generated ID.
         */
        enrollmentRepository.flush();

        List<StudentProgressionItem> progressionItems =
                createProgressionItems(
                        operationId,
                        organizationId,
                        plan,
                        targetBySourceId
                );

        itemRepository.saveAllAndFlush(progressionItems);

        StudentProgressionConfirmationResponse response =
                createResponse(
                        operation,
                        requestHash,
                        plan,
                        targetBySourceId
                );

        String resultJson = writeResult(response);

        operation.markSucceeded(resultJson);
        operationRepository.saveAndFlush(operation);

        return response;
    }

    private void validateOperation(
            StudentProgressionOperation operation,
            String requestHash,
            long targetAcademicYearId,
            StudentProgressionConfirmationRequest request
    ) {
        if (operation.getStatus()
                != StudentProgressionOperationStatus.RUNNING) {
            throw new StudentProgressionConflictException(
                    "Student progression operation is not running",
                    operation.getStatus(),
                    operation.getFailureCode() == null
                            ? "OPERATION_NOT_RUNNING"
                            : operation.getFailureCode()
            );
        }

        if (!Objects.equals(
                operation.getRequestHash(),
                requestHash
        )) {
            throw new StudentProgressionConflictException(
                    "Progression request does not match the registered operation",
                    StudentProgressionOperationStatus.CONFLICTED,
                    "REQUEST_HASH_MISMATCH"
            );
        }

        if (!Objects.equals(
                operation.getSourceAcademicYearId(),
                request.sourceAcademicYearId()
        )
                || !Objects.equals(
                operation.getTargetAcademicYearId(),
                targetAcademicYearId
        )) {
            throw new StudentProgressionConflictException(
                    "Progression academic years do not match the registered operation",
                    StudentProgressionOperationStatus.CONFLICTED,
                    "OPERATION_YEAR_MISMATCH"
            );
        }

        if (!Objects.equals(
                operation.getPreviewFingerprint(),
                request.previewFingerprint()
        )) {
            throw new StudentProgressionConflictException(
                    "Confirmation fingerprint does not match the registered operation",
                    StudentProgressionOperationStatus.CONFLICTED,
                    "OPERATION_FINGERPRINT_MISMATCH"
            );
        }

        if (operation.getTotalItems()
                != request.decisions().size()) {
            throw new StudentProgressionConflictException(
                    "Progression item count does not match the registered operation",
                    StudentProgressionOperationStatus.CONFLICTED,
                    "OPERATION_ITEM_COUNT_MISMATCH"
            );
        }
    }

    private Map<Long, StudentEnrollment> createTargetEnrollments(
            long organizationId,
            StudentProgressionPlan plan
    ) {
        Map<Long, StudentEnrollment> targetBySourceId =
                new HashMap<>();

        List<StudentEnrollment> targetEnrollments =
                new ArrayList<>();

        for (StudentProgressionPlanItem item : plan.items()) {
            if (!createsTargetEnrollment(
                    item.decision().outcome()
            )) {
                continue;
            }

            StudentEnrollment source = item.sourceEnrollment();

            StudentEnrollment target = new StudentEnrollment(
                    organizationId,
                    plan.targetAcademicYear().getId(),
                    item.targetGradeLevel().getId(),
                    item.targetSection().getId(),
                    source.getStudentUserId(),
                    item.decision().targetRollNumber(),
                    plan.targetAcademicYear().getStartDate()
            );

            targetEnrollments.add(target);
            targetBySourceId.put(source.getId(), target);
        }

        if (!targetEnrollments.isEmpty()) {
            enrollmentRepository.saveAllAndFlush(
                    targetEnrollments
            );
        }

        return targetBySourceId;
    }

    private void updateSourceEnrollments(
            StudentProgressionPlan plan
    ) {
        List<StudentEnrollment> sourceEnrollments =
                new ArrayList<>();

        for (StudentProgressionPlanItem item : plan.items()) {
            StudentEnrollment source = item.sourceEnrollment();
            StudentProgressionOutcome outcome =
                    item.decision().outcome();

            switch (outcome) {
                case PROMOTED, REPEATED, GRADUATED ->
                        source.markCompleted(
                                item.decision().effectiveOn()
                        );

                case LEFT ->
                        source.markWithdrawn(
                                item.decision().effectiveOn()
                        );

                case MANUAL_REVIEW ->
                        throw new StudentProgressionConflictException(
                                "Manual-review decisions cannot be confirmed"
                        );
            }

            sourceEnrollments.add(source);
        }

        enrollmentRepository.saveAll(sourceEnrollments);
    }

    private List<StudentProgressionItem> createProgressionItems(
            long operationId,
            long organizationId,
            StudentProgressionPlan plan,
            Map<Long, StudentEnrollment> targetBySourceId
    ) {
        List<StudentProgressionItem> items =
                new ArrayList<>();

        for (StudentProgressionPlanItem planItem : plan.items()) {
            StudentEnrollment source =
                    planItem.sourceEnrollment();

            StudentEnrollment target =
                    targetBySourceId.get(source.getId());

            items.add(new StudentProgressionItem(
                    operationId,
                    organizationId,
                    source.getId(),
                    source.getStudentUserId(),
                    planItem.decision().outcome(),
                    target == null ? null : target.getId(),
                    planItem.decision().effectiveOn(),
                    planItem.decision().note()
            ));
        }

        return items;
    }

    private StudentProgressionConfirmationResponse createResponse(
            StudentProgressionOperation operation,
            String requestHash,
            StudentProgressionPlan plan,
            Map<Long, StudentEnrollment> targetBySourceId
    ) {
        List<StudentProgressionItemResultResponse> results =
                plan.items()
                        .stream()
                        .map(item -> {
                            StudentEnrollment source =
                                    item.sourceEnrollment();

                            StudentEnrollment target =
                                    targetBySourceId.get(
                                            source.getId()
                                    );

                            return new StudentProgressionItemResultResponse(
                                    source.getId(),
                                    source.getStudentUserId(),
                                    item.decision().outcome(),
                                    source.getStatus(),
                                    target == null
                                            ? null
                                            : target.getId()
                            );
                        })
                        .toList();

        return new StudentProgressionConfirmationResponse(
                operation.getId(),
                StudentProgressionOperationStatus.SUCCEEDED,
                plan.sourceAcademicYear().getId(),
                plan.targetAcademicYear().getId(),
                plan.batchLabel(),
                requestHash,
                plan.previewFingerprint(),
                new StudentProgressionCountsResponse(
                        plan.totalItems(),
                        Math.toIntExact(plan.promotedCount()),
                        Math.toIntExact(plan.repeatedCount()),
                        Math.toIntExact(plan.graduatedCount()),
                        Math.toIntExact(plan.leftCount()),
                        Math.toIntExact(plan.manualReviewCount())
                ),
                results
        );
    }

    private boolean createsTargetEnrollment(
            StudentProgressionOutcome outcome
    ) {
        return outcome == StudentProgressionOutcome.PROMOTED
                || outcome == StudentProgressionOutcome.REPEATED;
    }

    private String writeResult(
            StudentProgressionConfirmationResponse response
    ) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Student progression result could not be stored",
                    exception
            );
        }
    }
}