package com.dawnrise.academic.studentattendance.policy.service.impl;

import com.dawnrise.academic.studentattendance.config.StudentAttendanceProperties;
import com.dawnrise.academic.studentattendance.policy.dto.*;
import com.dawnrise.academic.studentattendance.policy.entity.StudentAttendancePolicy;
import com.dawnrise.academic.studentattendance.policy.entity.StudentAttendanceStatusPolicy;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.policy.exception.*;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendancePolicyRepository;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendanceStatusPolicyRepository;
import com.dawnrise.academic.studentattendance.policy.service.StudentAttendancePolicyService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class StudentAttendancePolicyServiceImpl
        implements StudentAttendancePolicyService {

    private final StudentAttendancePolicyRepository policyRepository;
    private final StudentAttendanceStatusPolicyRepository statusRepository;
    private final StudentAttendanceProperties properties;

    public StudentAttendancePolicyServiceImpl(
            StudentAttendancePolicyRepository policyRepository,
            StudentAttendanceStatusPolicyRepository statusRepository,
            StudentAttendanceProperties properties
    ) {
        this.policyRepository = policyRepository;
        this.statusRepository = statusRepository;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentAttendancePolicyResponse get(long organizationId) {
        StudentAttendancePolicy policy = policyRepository
                .findById(organizationId)
                .orElseThrow(() ->
                        new StudentAttendancePolicyNotFoundException(
                                "Student attendance policy is not configured"
                        )
                );
        return toResponse(policy, statusRepository
                .findAllByOrganizationId(organizationId));
    }

    @Override
    public StudentAttendancePolicyResponse initialize(
            long organizationId,
            long actorUserId
    ) {
        if (policyRepository.existsById(organizationId)) {
            throw alreadyConfigured();
        }

        StudentAttendancePolicy policy = new StudentAttendancePolicy(
                organizationId,
                properties.getDefaultAttendanceMode(),
                properties.getDefaultWeekStartDay(),
                properties.getDefaultDraftWarningMinutes(),
                properties.getDefaultAutomaticSubmissionMinutes(),
                properties.getDefaultLatePenaltyEnabled(),
                properties.getDefaultLateOccurrencesThreshold(),
                properties.getDefaultLatePenaltyOutcome(),
                properties.getDefaultLateCountingPeriod(),
                actorUserId
        );

        try {
            StudentAttendancePolicy savedPolicy =
                    policyRepository.saveAndFlush(policy);
            List<StudentAttendanceStatusPolicy> savedStatuses =
                    statusRepository.saveAllAndFlush(AttendanceStatus
                            .finalStatuses()
                            .stream()
                            .map(status -> defaultStatusPolicy(
                                    organizationId,
                                    status
                            ))
                            .toList());

            return toResponse(savedPolicy, savedStatuses);
        } catch (DataIntegrityViolationException exception) {
            throw alreadyConfigured(exception);
        }
    }

    @Override
    public StudentAttendancePolicyResponse update(
            long organizationId,
            long actorUserId,
            StudentAttendancePolicyRequest request
    ) {
        validateStatusPolicies(request.statusPolicies());

        if (request.expectedVersion() == null) {
            throw new InvalidStudentAttendancePolicyException(
                    "Expected version is required when updating the policy"
            );
        }

        StudentAttendancePolicy policy = policyRepository
                .findById(organizationId)
                .orElseThrow(() ->
                        new StudentAttendancePolicyNotFoundException(
                                "Student attendance policy is not configured"
                        )
                );

        if (policy.getVersion() != null
                && !Objects.equals(policy.getVersion(),
                request.expectedVersion())) {
            throw stalePolicyConflict();
        }

        policy.update(
                request.attendanceMode(),
                request.weekStartDay(),
                request.draftWarningMinutes(),
                request.automaticSubmissionMinutes(),
                request.latePenaltyEnabled(),
                request.lateOccurrencesThreshold(),
                request.latePenaltyOutcome(),
                request.lateCountingPeriod(),
                actorUserId
        );

        StudentAttendancePolicy savedPolicy =
                policyRepository.saveAndFlush(policy);
        StudentAttendancePolicy claimedPolicy = claimAggregateVersion(
                organizationId,
                request.expectedVersion(),
                savedPolicy
        );

        Map<AttendanceStatus, StudentAttendanceStatusPolicy> existingStatuses =
                statusRepository.findAllByOrganizationId(organizationId)
                        .stream()
                        .collect(Collectors.toMap(
                                StudentAttendanceStatusPolicy::getAttendanceStatus,
                                Function.identity()
                        ));

        List<StudentAttendanceStatusPolicy> savedStatuses =
                statusRepository.saveAllAndFlush(request.statusPolicies()
                        .stream()
                        .map(item -> {
                            StudentAttendanceStatusPolicy existing =
                                    existingStatuses.get(item.attendanceStatus());
                            if (existing != null) {
                                existing.updateCredits(
                                        item.earnedCredit(),
                                        item.possibleCredit()
                                );
                                return existing;
                            }
                            return new StudentAttendanceStatusPolicy(
                                organizationId,
                                item.attendanceStatus(),
                                item.earnedCredit(),
                                item.possibleCredit()
                            );
                        })
                        .toList());

        return toResponse(claimedPolicy, savedStatuses);
    }

    private StudentAttendancePolicy claimAggregateVersion(
            long organizationId,
            Long expectedVersion,
            StudentAttendancePolicy savedPolicy
    ) {
        Long savedVersion = savedPolicy.getVersion();
        if (savedVersion == null
                || Objects.equals(savedVersion, expectedVersion)) {
            int updated = policyRepository.incrementVersionIfCurrent(
                    organizationId,
                    expectedVersion
            );
            if (updated == 0) {
                throw stalePolicyConflict();
            }
        }

        return policyRepository.findById(organizationId)
                .orElseThrow(() ->
                        new StudentAttendancePolicyNotFoundException(
                                "Student attendance policy is not configured"
                        )
                );
    }

    private StudentAttendanceStatusPolicy defaultStatusPolicy(
            long organizationId,
            AttendanceStatus status
    ) {
        StudentAttendanceProperties.StatusCredit credits =
                properties.getDefaultStatusCredits().get(status);
        return new StudentAttendanceStatusPolicy(
                organizationId,
                status,
                credits.getEarnedCredit(),
                credits.getPossibleCredit()
        );
    }

    private void validateStatusPolicies(
            List<AttendanceStatusPolicyRequest> statusPolicies
    ) {
        if (statusPolicies == null) {
            throw new InvalidStudentAttendancePolicyException(
                    "Attendance status policies are required"
            );
        }
        Set<AttendanceStatus> statuses = EnumSet.noneOf(AttendanceStatus.class);
        for (AttendanceStatusPolicyRequest item : statusPolicies) {
            if (item == null || item.attendanceStatus() == null) {
                throw new InvalidStudentAttendancePolicyException(
                        "Attendance status is required"
                );
            }
            if (!AttendanceStatus.finalStatuses()
                    .contains(item.attendanceStatus())) {
                throw new InvalidStudentAttendancePolicyException(
                        "Only final attendance statuses are supported"
                );
            }
            if (!statuses.add(item.attendanceStatus())) {
                throw new InvalidStudentAttendancePolicyException(
                        "Duplicate attendance status policy"
                );
            }
        }
        if (!statuses.equals(AttendanceStatus.finalStatuses())) {
            throw new InvalidStudentAttendancePolicyException(
                    "Exactly five final attendance status policies are required"
            );
        }
    }

    private StudentAttendancePolicyResponse toResponse(
            StudentAttendancePolicy policy,
            List<StudentAttendanceStatusPolicy> statuses
    ) {
        return new StudentAttendancePolicyResponse(
                policy.getOrganizationId(),
                policy.getAttendanceMode(),
                policy.getWeekStartDay(),
                policy.getDraftWarningMinutes(),
                policy.getAutomaticSubmissionMinutes(),
                policy.getLatePenaltyEnabled(),
                policy.getLateOccurrencesThreshold(),
                policy.getLatePenaltyOutcome(),
                policy.getLateCountingPeriod(),
                policy.getVersion(),
                policy.getCreatedAt(),
                policy.getUpdatedAt(),
                statuses.stream()
                        .sorted(Comparator.comparing(status ->
                                status.getAttendanceStatus().name()))
                        .map(status -> new AttendanceStatusPolicyResponse(
                                status.getAttendanceStatus(),
                                status.getEarnedCredit(),
                                status.getPossibleCredit(),
                                status.getVersion()
                        ))
                        .collect(Collectors.toList())
        );
    }

    private StudentAttendancePolicyConflictException alreadyConfigured() {
        return new StudentAttendancePolicyConflictException(
                "Student attendance policy is already configured"
        );
    }

    private StudentAttendancePolicyConflictException alreadyConfigured(
            Throwable cause
    ) {
        return new StudentAttendancePolicyConflictException(
                "Student attendance policy is already configured",
                cause
        );
    }

    private StudentAttendancePolicyConflictException stalePolicyConflict() {
        return new StudentAttendancePolicyConflictException(
                "Student attendance policy was modified by another request"
        );
    }
}
