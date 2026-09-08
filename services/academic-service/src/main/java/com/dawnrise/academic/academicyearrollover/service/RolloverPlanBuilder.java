package com.dawnrise.academic.academicyearrollover.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.exception.AcademicYearNotFoundException;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.academicyearrollover.dto.AcademicYearStructureRolloverRequest;
import com.dawnrise.academic.academicyearrollover.dto.RolloverConflictResponse;
import com.dawnrise.academic.academicyearrollover.exception.InvalidRolloverRequestException;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.exception.GradeLevelNotFoundException;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import com.dawnrise.academic.gradelevelsubject.repository.GradeLevelSubjectRepository;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.subject.entity.Subject;
import com.dawnrise.academic.subject.exception.SubjectNotFoundException;
import com.dawnrise.academic.subject.repository.SubjectRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RolloverPlanBuilder {

    public static final int MAX_GENERATED_STRUCTURE_RECORDS = 2_000;

    private final AcademicYearRepository academicYearRepository;
    private final GradeLevelRepository gradeLevelRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final GradeLevelSubjectRepository gradeLevelSubjectRepository;
    private final RolloverFingerprintService fingerprintService;

    public RolloverPlanBuilder(
            AcademicYearRepository academicYearRepository,
            GradeLevelRepository gradeLevelRepository,
            SectionRepository sectionRepository,
            SubjectRepository subjectRepository,
            GradeLevelSubjectRepository gradeLevelSubjectRepository,
            RolloverFingerprintService fingerprintService
    ) {
        this.academicYearRepository = academicYearRepository;
        this.gradeLevelRepository = gradeLevelRepository;
        this.sectionRepository = sectionRepository;
        this.subjectRepository = subjectRepository;
        this.gradeLevelSubjectRepository = gradeLevelSubjectRepository;
        this.fingerprintService = fingerprintService;
    }

    @Transactional(readOnly = true)
    public RolloverPlan build(
            long organizationId,
            long targetAcademicYearId,
            AcademicYearStructureRolloverRequest request
    ) {
        RolloverOptions options = normalize(targetAcademicYearId, request);
        AcademicYear source = academicYearRepository
                .findByIdAndOrganizationId(
                        options.sourceAcademicYearId(),
                        organizationId
                )
                .orElseThrow(() -> new AcademicYearNotFoundException(
                        "Academic year not found"
                ));
        AcademicYear target = academicYearRepository
                .findByIdAndOrganizationId(
                        options.targetAcademicYearId(),
                        organizationId
                )
                .orElseThrow(() -> new AcademicYearNotFoundException(
                        "Academic year not found"
                ));

        validateAcademicYears(source, target);

        List<GradeLevel> allSourceGrades = gradeLevelRepository
                .findAllByOrganizationIdAndAcademicYearIdOrderByDisplayOrderAscIdAsc(
                        organizationId,
                        source.getId()
                );
        List<Subject> allSourceSubjects = subjectRepository
                .findAllByOrganizationIdAndAcademicYearIdOrderByCodeAscIdAsc(
                        organizationId,
                        source.getId()
                );

        List<GradeLevel> selectedGrades =
                selectGrades(allSourceGrades, options.gradeLevelIds());
        List<Subject> selectedSubjects =
                selectSubjects(allSourceSubjects, options.subjectIds());

        Set<Long> selectedGradeIds = selectedGrades.stream()
                .map(GradeLevel::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> selectedSubjectIds = selectedSubjects.stream()
                .map(Subject::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Section> sourceSections = options.includeSections()
                ? sectionRepository
                        .findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdInOrderByGradeLevelIdAscDisplayOrderAscIdAsc(
                                organizationId,
                                source.getId(),
                                selectedGradeIds
                        )
                : List.of();
        List<GradeLevelSubject> sourceAssignments =
                options.includeGradeLevelSubjects()
                        ? gradeLevelSubjectRepository
                        .findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdInOrderByGradeLevelIdAscDisplayOrderAscIdAsc(
                                organizationId,
                                source.getId(),
                                selectedGradeIds
                        )
                        .stream()
                        .filter(assignment -> selectedSubjectIds.contains(
                                assignment.getSubjectId()
                        ))
                        .toList()
                        : List.of();

        List<GradeLevel> sourceGrades = options.includeGradeLevels()
                ? selectedGrades
                : List.of();
        List<Subject> sourceSubjects = options.includeSubjects()
                ? selectedSubjects
                : List.of();

        int generated = sourceGrades.size()
                + sourceSections.size()
                + sourceSubjects.size()
                + sourceAssignments.size();
        if (generated > MAX_GENERATED_STRUCTURE_RECORDS) {
            throw new InvalidRolloverRequestException(
                    "The rollover would create too many structure records"
            );
        }

        List<GradeLevel> targetGrades = gradeLevelRepository
                .findAllByOrganizationIdAndAcademicYearIdOrderByDisplayOrderAscIdAsc(
                        organizationId,
                        target.getId()
                );
        List<Subject> targetSubjects = subjectRepository
                .findAllByOrganizationIdAndAcademicYearIdOrderByCodeAscIdAsc(
                        organizationId,
                        target.getId()
                );
        List<Section> targetSections = sectionRepository
                .findAllByOrganizationIdAndAcademicYearIdOrderByGradeLevelIdAscDisplayOrderAscIdAsc(
                        organizationId,
                        target.getId()
                );
        List<GradeLevelSubject> targetAssignments =
                gradeLevelSubjectRepository
                        .findAllByOrganizationIdAndAcademicYearIdOrderByGradeLevelIdAscDisplayOrderAscIdAsc(
                                organizationId,
                                target.getId()
                        );

        List<RolloverConflictResponse> conflicts = detectConflicts(
                sourceGrades,
                sourceSections,
                sourceSubjects,
                sourceAssignments,
                targetGrades,
                targetSections,
                targetSubjects,
                targetAssignments
        );

        RolloverOptions canonicalOptions = new RolloverOptions(
                options.sourceAcademicYearId(),
                options.targetAcademicYearId(),
                options.includeGradeLevels(),
                options.includeSections(),
                options.includeSubjects(),
                options.includeGradeLevelSubjects(),
                List.copyOf(options.gradeLevelIds()),
                List.copyOf(options.subjectIds())
        );
        String requestHash =
                fingerprintService.requestHash(organizationId, canonicalOptions);
        RolloverPlan planWithoutFingerprint = new RolloverPlan(
                canonicalOptions,
                source,
                target,
                List.copyOf(sourceGrades),
                List.copyOf(sourceSections),
                List.copyOf(sourceSubjects),
                List.copyOf(sourceAssignments),
                List.copyOf(targetGrades),
                List.copyOf(targetSections),
                List.copyOf(targetSubjects),
                List.copyOf(targetAssignments),
                List.copyOf(conflicts),
                requestHash,
                null
        );
        return new RolloverPlan(
                canonicalOptions,
                source,
                target,
                planWithoutFingerprint.sourceGradeLevels(),
                planWithoutFingerprint.sourceSections(),
                planWithoutFingerprint.sourceSubjects(),
                planWithoutFingerprint.sourceGradeLevelSubjects(),
                planWithoutFingerprint.targetGradeLevels(),
                planWithoutFingerprint.targetSections(),
                planWithoutFingerprint.targetSubjects(),
                planWithoutFingerprint.targetGradeLevelSubjects(),
                planWithoutFingerprint.conflicts(),
                requestHash,
                fingerprintService.previewFingerprint(planWithoutFingerprint)
        );
    }

    public RolloverOptions normalize(
            long targetAcademicYearId,
            AcademicYearStructureRolloverRequest request
    ) {
        boolean includeGrades = Boolean.TRUE.equals(request.includeGradeLevels());
        boolean includeSections = Boolean.TRUE.equals(request.includeSections());
        boolean includeSubjects = Boolean.TRUE.equals(request.includeSubjects());
        boolean includeAssignments =
                Boolean.TRUE.equals(request.includeGradeLevelSubjects());

        if (!includeGrades && !includeSections
                && !includeSubjects && !includeAssignments) {
            throw new InvalidRolloverRequestException(
                    "At least one rollover option must be selected"
            );
        }
        if (includeSections && !includeGrades) {
            throw new InvalidRolloverRequestException(
                    "Sections can only be copied when grade levels are copied"
            );
        }
        if (includeAssignments && (!includeGrades || !includeSubjects)) {
            throw new InvalidRolloverRequestException(
                    "Grade-subject mappings can only be copied when grade levels and subjects are copied"
            );
        }
        if (Objects.equals(request.sourceAcademicYearId(), targetAcademicYearId)) {
            throw new InvalidRolloverRequestException(
                    "Source and target academic years must be different"
            );
        }

        return new RolloverOptions(
                request.sourceAcademicYearId(),
                targetAcademicYearId,
                includeGrades,
                includeSections,
                includeSubjects,
                includeAssignments,
                validateFilter("grade level", request.gradeLevelIds()),
                validateFilter("subject", request.subjectIds())
        );
    }

    private List<Long> validateFilter(String label, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> seen = new HashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new InvalidRolloverRequestException(
                        "Filter IDs must be positive"
                );
            }
            if (!seen.add(id)) {
                throw new InvalidRolloverRequestException(
                        "Duplicate " + label + " filter IDs are not allowed"
                );
            }
        }
        return ids.stream().sorted().toList();
    }

    private void validateAcademicYears(AcademicYear source, AcademicYear target) {
        if (source.getStatus() != AcademicYearStatus.ACTIVE
                && source.getStatus() != AcademicYearStatus.CLOSED) {
            throw new InvalidRolloverRequestException(
                    "Source academic year must be active or closed"
            );
        }
        if (target.getStatus() != AcademicYearStatus.PLANNED) {
            throw new InvalidRolloverRequestException(
                    "Target academic year must be planned"
            );
        }
        if (!target.getStartDate().isAfter(source.getEndDate())) {
            throw new InvalidRolloverRequestException(
                    "Target academic year must begin after the source academic year ends"
            );
        }
    }

    private List<GradeLevel> selectGrades(
            List<GradeLevel> allGrades,
            List<Long> filters
    ) {
        if (filters.isEmpty()) {
            return allGrades;
        }
        Map<Long, GradeLevel> byId = allGrades.stream()
                .collect(Collectors.toMap(GradeLevel::getId, Function.identity()));
        List<GradeLevel> selected = new ArrayList<>();
        for (Long id : filters) {
            GradeLevel grade = byId.get(id);
            if (grade == null) {
                throw new GradeLevelNotFoundException("Grade level not found");
            }
            selected.add(grade);
        }
        selected.sort(Comparator.comparing(GradeLevel::getDisplayOrder)
                .thenComparing(GradeLevel::getId));
        return selected;
    }

    private List<Subject> selectSubjects(
            List<Subject> allSubjects,
            List<Long> filters
    ) {
        if (filters.isEmpty()) {
            return allSubjects;
        }
        Map<Long, Subject> byId = allSubjects.stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity()));
        List<Subject> selected = new ArrayList<>();
        for (Long id : filters) {
            Subject subject = byId.get(id);
            if (subject == null) {
                throw new SubjectNotFoundException("Subject not found");
            }
            selected.add(subject);
        }
        selected.sort(Comparator.comparing(Subject::getCode)
                .thenComparing(Subject::getId));
        return selected;
    }

    private List<RolloverConflictResponse> detectConflicts(
            List<GradeLevel> sourceGrades,
            List<Section> sourceSections,
            List<Subject> sourceSubjects,
            List<GradeLevelSubject> sourceAssignments,
            List<GradeLevel> targetGrades,
            List<Section> targetSections,
            List<Subject> targetSubjects,
            List<GradeLevelSubject> targetAssignments
    ) {
        List<RolloverConflictResponse> conflicts = new ArrayList<>();
        Set<String> gradeCodes = targetGrades.stream()
                .map(grade -> grade.getCode().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> gradeNames = targetGrades.stream()
                .map(grade -> grade.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<Integer> gradeOrders = targetGrades.stream()
                .map(GradeLevel::getDisplayOrder)
                .collect(Collectors.toSet());
        for (GradeLevel grade : sourceGrades) {
            addIf(conflicts, gradeCodes.contains(
                    grade.getCode().toLowerCase(Locale.ROOT)
            ), "TARGET_GRADE_LEVEL_CODE_EXISTS", "GRADE_LEVEL",
                    grade.getId(), "code", grade.getCode());
            addIf(conflicts, gradeNames.contains(
                    grade.getName().toLowerCase(Locale.ROOT)
            ), "TARGET_GRADE_LEVEL_NAME_EXISTS", "GRADE_LEVEL",
                    grade.getId(), "name", grade.getName());
            addIf(conflicts, gradeOrders.contains(grade.getDisplayOrder()),
                    "TARGET_GRADE_LEVEL_DISPLAY_ORDER_EXISTS", "GRADE_LEVEL",
                    grade.getId(), "displayOrder",
                    String.valueOf(grade.getDisplayOrder()));
        }

        Set<String> subjectCodes = targetSubjects.stream()
                .map(subject -> subject.getCode().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> subjectNames = targetSubjects.stream()
                .map(subject -> subject.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        for (Subject subject : sourceSubjects) {
            addIf(conflicts, subjectCodes.contains(
                    subject.getCode().toLowerCase(Locale.ROOT)
            ), "TARGET_SUBJECT_CODE_EXISTS", "SUBJECT",
                    subject.getId(), "code", subject.getCode());
            addIf(conflicts, subjectNames.contains(
                    subject.getName().toLowerCase(Locale.ROOT)
            ), "TARGET_SUBJECT_NAME_EXISTS", "SUBJECT",
                    subject.getId(), "name", subject.getName());
        }

        if (!targetSections.isEmpty()) {
            for (Section section : sourceSections) {
                conflicts.add(new RolloverConflictResponse(
                        "TARGET_SECTIONS_EXIST",
                        "SECTION",
                        section.getId(),
                        "targetAcademicYearId",
                        targetSections.get(0).getAcademicYearId().toString(),
                        "Target academic year already contains sections"
                ));
            }
        }

        if (!targetAssignments.isEmpty()) {
            for (GradeLevelSubject assignment : sourceAssignments) {
                conflicts.add(new RolloverConflictResponse(
                        "TARGET_GRADE_SUBJECTS_EXIST",
                        "GRADE_LEVEL_SUBJECT",
                        assignment.getId(),
                        "targetAcademicYearId",
                        targetAssignments.get(0).getAcademicYearId().toString(),
                        "Target academic year already contains grade-subject mappings"
                ));
            }
        }

        return conflicts;
    }

    private void addIf(
            List<RolloverConflictResponse> conflicts,
            boolean condition,
            String type,
            String entityType,
            Long sourceId,
            String field,
            String value
    ) {
        if (condition) {
            conflicts.add(new RolloverConflictResponse(
                    type,
                    entityType,
                    sourceId,
                    field,
                    value,
                    "Target academic year already contains conflicting structure"
            ));
        }
    }
}
