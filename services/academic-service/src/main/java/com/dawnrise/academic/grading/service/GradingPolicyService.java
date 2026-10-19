package com.dawnrise.academic.grading.service;

import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.grading.dto.GradingPolicyRequest;
import com.dawnrise.academic.grading.dto.GradingPolicyResponse;
import com.dawnrise.academic.grading.entity.GradingPolicy;
import com.dawnrise.academic.grading.enums.GradingPolicyStatus;
import com.dawnrise.academic.grading.repository.GradingPolicyRepository;
import com.dawnrise.academic.results.exception.ResultNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GradingPolicyService {
    private final GradingPolicyRepository policies;
    private final AcademicYearRepository years;
    private final GradeLevelRepository grades;

    public GradingPolicyService(
            GradingPolicyRepository policies,
            AcademicYearRepository years,
            GradeLevelRepository grades
    ) {
        this.policies = policies;
        this.years = years;
        this.grades = grades;
    }

    @Transactional(readOnly = true)
    public List<GradingPolicyResponse> list(long organizationId, long academicYearId, long gradeLevelId) {
        requireScope(organizationId, academicYearId, gradeLevelId);
        return policies.findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdOrderByCreatedAtDesc(
                organizationId, academicYearId, gradeLevelId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public GradingPolicyResponse detail(long organizationId, long id) {
        return response(get(organizationId, id));
    }

    @Transactional
    public GradingPolicyResponse create(long organizationId, long actorUserId, GradingPolicyRequest request) {
        requireScope(organizationId, request.academicYearId(), request.gradeLevelId());
        return response(policies.saveAndFlush(new GradingPolicy(organizationId, actorUserId, request)));
    }

    @Transactional
    public GradingPolicyResponse update(
            long organizationId,
            long actorUserId,
            long id,
            GradingPolicyRequest request
    ) {
        requireScope(organizationId, request.academicYearId(), request.gradeLevelId());
        GradingPolicy policy = get(organizationId, id);
        policy.update(request, actorUserId);
        return response(policy);
    }

    @Transactional
    public GradingPolicyResponse activate(long organizationId, long actorUserId, long id) {
        GradingPolicy policy = get(organizationId, id);
        grades.findScopeForUpdate(policy.getGradeLevelId(), policy.getAcademicYearId(), organizationId)
                .orElseThrow(() -> new ResultNotFoundException("Grade level not found"));
        List<GradingPolicy> scope = policies.lockScope(
                organizationId, policy.getAcademicYearId(), policy.getGradeLevelId());
        int nextVersion = scope.stream().mapToInt(GradingPolicy::getPolicyVersion).max().orElse(0) + 1;
        scope.stream()
                .filter(candidate -> candidate.getStatus() == GradingPolicyStatus.ACTIVE)
                .forEach(candidate -> candidate.retire(actorUserId));
        policy.activate(actorUserId, nextVersion);
        return response(policies.saveAndFlush(policy));
    }

    private void requireScope(long organizationId, long academicYearId, long gradeLevelId) {
        years.findByIdAndOrganizationId(academicYearId, organizationId)
                .orElseThrow(() -> new ResultNotFoundException("Academic year not found"));
        grades.findByIdAndAcademicYearIdAndOrganizationId(gradeLevelId, academicYearId, organizationId)
                .orElseThrow(() -> new ResultNotFoundException("Grade level not found"));
    }

    private GradingPolicy get(long organizationId, long id) {
        return policies.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResultNotFoundException("Grading policy not found"));
    }

    private GradingPolicyResponse response(GradingPolicy policy) {
        return new GradingPolicyResponse(
                policy.getId(), policy.getAcademicYearId(), policy.getGradeLevelId(), policy.getName(),
                policy.getNotes(), policy.getStatus(), policy.getSubjectPassPercentage(),
                policy.getOverallPassPercentage(), policy.getAbsentCountsAsFailure(),
                policy.getRequireEverySubjectToPass(), policy.getDecimalScale(), policy.getRoundingMode(),
                policy.getPolicyVersion(), policy.getVersion(), policy.bandResponses(),
                policy.getCreatedAt(), policy.getUpdatedAt());
    }
}
