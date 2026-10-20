package com.dawnrise.academic.reportcard.service;

import com.dawnrise.academic.grading.entity.GradingPolicy;
import com.dawnrise.academic.grading.enums.GradingPolicyStatus;
import com.dawnrise.academic.grading.repository.GradingPolicyRepository;
import com.dawnrise.academic.reportcard.dto.*;
import com.dawnrise.academic.reportcard.entity.*;
import com.dawnrise.academic.reportcard.enums.ReportCardStatus;
import com.dawnrise.academic.reportcard.repository.ReportCardRepository;
import com.dawnrise.academic.results.dto.*;
import com.dawnrise.academic.results.entity.ResultPublication;
import com.dawnrise.academic.results.enums.ResultPublicationStatus;
import com.dawnrise.academic.results.exception.*;
import com.dawnrise.academic.results.integration.identity.IdentityGuardianAccessClient;
import com.dawnrise.academic.results.repository.ResultPublicationRepository;
import com.dawnrise.academic.results.service.ResultPublicationService;
import com.dawnrise.academic.studentenrollment.integration.identity.BatchStudentEnrollmentEligibilityResponse;
import com.dawnrise.academic.studentenrollment.integration.identity.IdentityStudentEligibilityClient;
import com.dawnrise.academic.studentenrollment.integration.identity.IdentityStudentEligibilityException;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ReportCardService {
    private static final Logger log = LoggerFactory.getLogger(ReportCardService.class);
    private final ReportCardRepository cards;
    private final ResultPublicationRepository publications;
    private final GradingPolicyRepository policies;
    private final ResultPublicationService results;
    private final IdentityGuardianAccessClient guardians;
    private final IdentityStudentEligibilityClient identityStudents;
    private final ReportCardCalculator calculator = new ReportCardCalculator();

    public ReportCardService(ReportCardRepository c, ResultPublicationRepository p, GradingPolicyRepository gp, ResultPublicationService r, IdentityGuardianAccessClient g, IdentityStudentEligibilityClient identityStudents) {
        cards = c;
        publications = p;
        policies = gp;
        results = r;
        guardians = g;
        this.identityStudents = identityStudents;
    }

    @Transactional(readOnly = true)
    public ReportCardReadinessResponse readiness(long o, long publicationId) {
        ResultPublication p = published(o, publicationId);
        var policy = policies.findByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndStatus(o, p.getAcademicYearId(), p.getGradeLevelId(), GradingPolicyStatus.ACTIVE);
        int students = results.studentIdsForPublication(o, publicationId).size();
        List<String> blockers = new ArrayList<>();
        if (policy.isEmpty()) blockers.add("No active grading policy exists for this academic year and grade");
        if (students == 0) blockers.add("Published result contains no student marks");
        return new ReportCardReadinessResponse(publicationId, blockers.isEmpty(), policy.map(GradingPolicy::getId).orElse(null), policy.map(GradingPolicy::getName).orElse(null), students, (int) cards.countByOrganizationIdAndPublicationId(o, publicationId), List.copyOf(blockers));
    }

    @Transactional
    public List<ReportCardResponse> generate(long o, long actor, long publicationId) {
        ResultPublication p = publications.findByIdAndOrganizationIdForUpdate(publicationId, o).filter(x -> x.getStatus() == ResultPublicationStatus.PUBLISHED).orElseThrow(() -> new ResultNotFoundException("Published result not found"));
        GradingPolicy policy = policies.findByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndStatus(o, p.getAcademicYearId(), p.getGradeLevelId(), GradingPolicyStatus.ACTIVE).orElseThrow(() -> new ResultConflictException("No active grading policy exists for this academic year and grade"));
        List<PublishedResultDetailResponse> sources = results.detailsForPublication(o, publicationId);
        if (sources.isEmpty()) throw new ResultConflictException("Published result contains no student marks");
        int subjectCount = results.publishedSubjectCount(o, publicationId);
        Map<Long, ReportCard> existingByStudent = cards.findAllByOrganizationIdAndPublicationIdOrderByRollNumberAscIdAsc(o, publicationId).stream().collect(java.util.stream.Collectors.toMap(ReportCard::getStudentUserId, java.util.function.Function.identity()));
        List<ReportCard> generated = new ArrayList<>();
        for (PublishedResultDetailResponse source : sources) {
            ReportCard existing = existingByStudent.get(source.studentUserId());
            if (existing != null) {
                generated.add(existing);
                continue;
            }
            var calc = calculator.calculate(policy, source.subjects().stream().map(s -> new ReportCardCalculator.Source(s.subjectId(), s.subjectCode(), s.subjectName(), s.maximumMarks(), s.marksObtained(), s.absent())).toList());
            List<ReportCardSubject> rows = calc.subjects().stream().map(s -> new ReportCardSubject(o, s.source().subjectId(), s.source().code(), s.source().name(), s.source().maximumMarks(), s.source().obtainedMarks(), s.source().absent(), s.percentage(), s.grade(), s.passed(), s.order())).toList();
            generated.add(cards.save(new ReportCard(o, publicationId, source.academicYearId(), source.examinationId(), source.gradeLevelId(), source.sectionId(), source.studentUserId(), source.enrollmentId(), policy.getId(), source.academicYearName(), source.examinationName(), source.gradeLevelName(), source.sectionName(), source.rollNumberSnapshot(), source.publishedAt(), policy.getName(), policy.getPolicyVersion(), policy.getSubjectPassPercentage(), policy.getOverallPassPercentage(), policy.getAbsentCountsAsFailure(), policy.getRequireEverySubjectToPass(), policy.getDecimalScale(), policy.getRoundingMode(), rows, calc.totalMaximum(), calc.totalObtained(), calc.aggregate(), calc.overallGrade(), calc.passed(), calc.absentCount(), source.subjects().size() < subjectCount, actor)));
        }
        try {
            cards.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ResultConflictException("Report cards were generated concurrently; reload the workspace");
        }
        return responses(o, generated);
    }

    @Transactional(readOnly = true)
    public List<ReportCardResponse> list(long o, long publication) {
        published(o, publication);
        return responses(o, cards.findAllByOrganizationIdAndPublicationIdOrderByRollNumberAscIdAsc(o, publication));
    }

    @Transactional(readOnly = true)
    public ReportCardResponse detail(long o, long id) {
        return response(o, owned(o, id));
    }

    @Transactional
    public ReportCardResponse submit(long o, long id, long version) {
        var c = locked(o, id);
        c.submit(version);
        return response(o, c);
    }

    @Transactional
    public ReportCardResponse approve(long o, long actor, long id, long version) {
        var c = locked(o, id);
        c.approve(version, actor);
        return response(o, c);
    }

    @Transactional
    public ReportCardResponse returnToDraft(long o, long actor, long id, long version, String reason) {
        var c = locked(o, id);
        c.returnToDraft(version, actor, reason);
        return response(o, c);
    }

    @Transactional
    public ReportCardResponse publish(long o, long actor, long id, long version) {
        var c = locked(o, id);
        c.publish(version, actor);
        return response(o, c);
    }

    @Transactional(readOnly = true)
    public List<ReportCardResponse> self(long o, long student) {
        return responses(o, cards.findAllByOrganizationIdAndStudentUserIdAndStatusOrderByPublishedAtDescIdDesc(o, student, ReportCardStatus.PUBLISHED));
    }

    @Transactional(readOnly = true)
    public ReportCardResponse selfDetail(long o, long student, long id) {
        var c = cards.findByIdAndOrganizationIdAndStatus(id, o, ReportCardStatus.PUBLISHED).filter(x -> x.getStudentUserId() == student).orElseThrow(() -> new ResultNotFoundException("Report card not found"));
        return response(o, c);
    }

    @Transactional(readOnly = true)
    public List<ReportCardResponse> parent(long o, long guardian, long student) {
        requireGuardian(o, guardian, student);
        return self(o, student);
    }

    @Transactional(readOnly = true)
    public ReportCardResponse parentDetail(long o, long guardian, long student, long id) {
        requireGuardian(o, guardian, student);
        return selfDetail(o, student, id);
    }

    private void requireGuardian(long o, long g, long s) {
        if (!guardians.hasActiveRelationship(o, g, s)) throw new ResultNotFoundException("Report card not found");
    }

    private ResultPublication published(long o, long id) {
        return publications.findByIdAndOrganizationIdAndStatus(id, o, ResultPublicationStatus.PUBLISHED).orElseThrow(() -> new ResultNotFoundException("Published result not found"));
    }

    private ReportCard owned(long o, long id) {
        return cards.findByIdAndOrganizationId(id, o).orElseThrow(() -> new ResultNotFoundException("Report card not found"));
    }

    private ReportCard locked(long o, long id) {
        return cards.findForUpdate(id, o).orElseThrow(() -> new ResultNotFoundException("Report card not found"));
    }

    private List<ReportCardResponse> responses(long organizationId, List<ReportCard> reportCards) {
        Map<Long, String> names = studentNames(organizationId, reportCards);
        return reportCards.stream().map(card -> response(card, names.get(card.getStudentUserId()))).toList();
    }

    private ReportCardResponse response(long organizationId, ReportCard card) {
        return response(card, studentNames(organizationId, List.of(card)).get(card.getStudentUserId()));
    }

    private Map<Long, String> studentNames(long organizationId, List<ReportCard> reportCards) {
        List<Long> ids = reportCards.stream().map(ReportCard::getStudentUserId).distinct().sorted().toList();
        if (ids.isEmpty()) return Map.of();
        try {
            BatchStudentEnrollmentEligibilityResponse result = identityStudents.checkBatch(organizationId, ids);
            if (result == null || result.results() == null) return Map.of();
            Map<Long, String> names = new HashMap<>();
            result.results().stream().filter(Objects::nonNull)
                    .filter(item -> Objects.equals(item.organizationId(), organizationId))
                    .filter(item -> item.displayName() != null && !item.displayName().isBlank())
                    .forEach(item -> names.put(item.userId(), item.displayName().trim()));
            return Map.copyOf(names);
        } catch (IdentityStudentEligibilityException | IllegalStateException exception) {
            log.warn("Student display names could not be enriched for report cards in organization {}", organizationId, exception);
            return Map.of();
        }
    }

    ReportCardResponse response(ReportCard c, String studentDisplayName) {
        return new ReportCardResponse(c.getId(), c.getPublicationId(), c.getAcademicYearId(), c.getExaminationId(), c.getGradeLevelId(), c.getSectionId(), c.getStudentUserId(), studentDisplayName, c.getEnrollmentId(), c.getStatus(), c.getAcademicYearName(), c.getExaminationName(), c.getGradeName(), c.getSectionName(), c.getRollNumber(), c.getResultPublishedAt(), c.getPolicyName(), c.getPolicyVersion(), c.getSubjectPassPercentage(), c.getOverallPassPercentage(), c.getAbsentCountsAsFailure(), c.getRequireEverySubjectToPass(), c.getDecimalScale(), c.getRoundingMode(), c.getSubjects().stream().map(s -> new ReportCardSubjectResponse(s.getSubjectId(), s.getSubjectCode(), s.getSubjectName(), s.getMaximumMarks(), s.getObtainedMarks(), s.getAbsent(), s.getPercentage(), s.getGradeLabel(), s.getPassed(), s.getDisplayOrder())).toList(), c.getTotalMaximumMarks(), c.getTotalObtainedMarks(), c.getAggregatePercentage(), c.getOverallGrade(), c.getPassed(), c.getAbsentSubjectCount(), c.getPartial(), c.getReturnReason(), c.getVersion(), c.getPublishedAt());
    }
}
