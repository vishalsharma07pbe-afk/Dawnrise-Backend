package com.dawnrise.academic.results.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.examination.entity.Examination;
import com.dawnrise.academic.examination.entity.ScheduledAssessment;
import com.dawnrise.academic.examination.enums.ExaminationStatus;
import com.dawnrise.academic.examination.repository.ExaminationRepository;
import com.dawnrise.academic.examination.repository.ScheduledAssessmentRepository;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import com.dawnrise.academic.gradelevelsubject.repository.GradeLevelSubjectRepository;
import com.dawnrise.academic.results.dto.PublishedResultDetailResponse;
import com.dawnrise.academic.results.dto.PublishedResultSubjectResponse;
import com.dawnrise.academic.results.dto.PublishedResultSummaryResponse;
import com.dawnrise.academic.results.dto.ResultPublicationReadinessResponse;
import com.dawnrise.academic.results.dto.ResultPublicationResponse;
import com.dawnrise.academic.results.dto.ResultPublicationSubjectReadinessResponse;
import com.dawnrise.academic.results.entity.ResultPublication;
import com.dawnrise.academic.results.entity.ResultSubjectMark;
import com.dawnrise.academic.results.entity.ResultSubjectSheet;
import com.dawnrise.academic.results.enums.ResultPublicationStatus;
import com.dawnrise.academic.results.enums.ResultSubjectSheetStatus;
import com.dawnrise.academic.results.exception.ResultConflictException;
import com.dawnrise.academic.results.exception.ResultNotFoundException;
import com.dawnrise.academic.results.integration.identity.IdentityGuardianAccessClient;
import com.dawnrise.academic.results.repository.ResultPublicationRepository;
import com.dawnrise.academic.results.repository.ResultSubjectMarkRepository;
import com.dawnrise.academic.results.repository.ResultSubjectSheetRepository;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentenrollment.repository.StudentEnrollmentRepository;
import com.dawnrise.academic.subject.entity.Subject;
import com.dawnrise.academic.subject.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ResultPublicationService {
    private final AcademicYearRepository years;
    private final ExaminationRepository examinations;
    private final ScheduledAssessmentRepository assessments;
    private final GradeLevelRepository grades;
    private final SectionRepository sections;
    private final GradeLevelSubjectRepository gradeSubjects;
    private final SubjectRepository subjects;
    private final StudentEnrollmentRepository enrollments;
    private final ResultSubjectSheetRepository sheets;
    private final ResultSubjectMarkRepository marks;
    private final ResultPublicationRepository publications;
    private final IdentityGuardianAccessClient guardianAccess;

    public ResultPublicationService(
            AcademicYearRepository years,
            ExaminationRepository examinations,
            ScheduledAssessmentRepository assessments,
            GradeLevelRepository grades,
            SectionRepository sections,
            GradeLevelSubjectRepository gradeSubjects,
            SubjectRepository subjects,
            StudentEnrollmentRepository enrollments,
            ResultSubjectSheetRepository sheets,
            ResultSubjectMarkRepository marks,
            ResultPublicationRepository publications,
            IdentityGuardianAccessClient guardianAccess
    ) {
        this.years = years;
        this.examinations = examinations;
        this.assessments = assessments;
        this.grades = grades;
        this.sections = sections;
        this.gradeSubjects = gradeSubjects;
        this.subjects = subjects;
        this.enrollments = enrollments;
        this.sheets = sheets;
        this.marks = marks;
        this.publications = publications;
        this.guardianAccess = guardianAccess;
    }

    @Transactional(readOnly = true)
    public ResultPublicationReadinessResponse readiness(
            long organizationId,
            long yearId,
            long examinationId,
            long gradeId,
            long sectionId
    ) {
        return calculate(organizationId, yearId, examinationId, gradeId, sectionId, false);
    }

    @Transactional
    public ResultPublicationResponse publish(
            long organizationId,
            long actorUserId,
            long yearId,
            long examinationId,
            long gradeId,
            long sectionId
    ) {
        examinations.findForUpdate(examinationId, yearId, organizationId)
                .orElseThrow(() -> new ResultNotFoundException("Examination not found"));
        Optional<ResultPublication> existing = publications.findScopeForUpdate(
                organizationId, yearId, examinationId, gradeId, sectionId);
        if (existing.isPresent()) {
            ResultPublication publication = existing.get();
            if (publication.getStatus() == ResultPublicationStatus.PUBLISHED) {
                return response(publication);
            }
            throw new ResultConflictException(
                    "A withdrawn result publication cannot be republished"
            );
        }
        sheets.findScopeForUpdate(organizationId, yearId, examinationId, gradeId, sectionId);
        ResultPublicationReadinessResponse readiness = calculate(
                organizationId, yearId, examinationId, gradeId, sectionId, true);
        if (!readiness.ready()) throw new ResultConflictException(
                "Results are not ready for publication: " + String.join("; ", readiness.blockers()));
        return response(publications.saveAndFlush(new ResultPublication(
                organizationId, yearId, examinationId, gradeId, sectionId, actorUserId)));
    }

    @Transactional(readOnly = true)
    public List<PublishedResultSummaryResponse> listForStudent(
            long organizationId,
            long studentUserId
    ) {
        return publications.findAllPublishedForStudent(
                        organizationId,
                        studentUserId
                ).stream()
                .map(this::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublishedResultDetailResponse detailForStudent(
            long organizationId,
            long studentUserId,
            long publicationId
    ) {
        ResultPublication publication = published(organizationId, publicationId);
        return detail(publication, studentUserId);
    }

    @Transactional(readOnly = true)
    public List<PublishedResultSummaryResponse> listForParent(
            long organizationId,
            long guardianUserId,
            long studentUserId
    ) {
        requireGuardian(organizationId, guardianUserId, studentUserId);
        return listForStudent(organizationId, studentUserId);
    }

    @Transactional(readOnly = true)
    public PublishedResultDetailResponse detailForParent(
            long organizationId,
            long guardianUserId,
            long studentUserId,
            long publicationId
    ) {
        requireGuardian(organizationId, guardianUserId, studentUserId);
        return detailForStudent(organizationId, studentUserId, publicationId);
    }

    private ResultPublicationReadinessResponse calculate(
            long organizationId,
            long yearId,
            long examinationId,
            long gradeId,
            long sectionId,
            boolean lockedSheetsAlready
    ) {
        AcademicYear year = years.findByIdAndOrganizationId(yearId, organizationId)
                .orElseThrow(() -> new ResultNotFoundException("Academic year not found"));
        Examination examination = examinations.findByIdAndAcademicYearIdAndOrganizationId(
                        examinationId, yearId, organizationId)
                .orElseThrow(() -> new ResultNotFoundException("Examination not found"));
        grades.findByIdAndAcademicYearIdAndOrganizationId(gradeId, yearId, organizationId)
                .orElseThrow(() -> new ResultNotFoundException("Grade level not found"));
        sections.findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                        sectionId, gradeId, yearId, organizationId)
                .orElseThrow(() -> new ResultNotFoundException("Section not found"));
        List<String> blockers = new ArrayList<>();
        if (year.getStatus() != AcademicYearStatus.ACTIVE) {
            blockers.add("Academic year is not active");
        }
        if (examination.getStatus() != ExaminationStatus.PUBLISHED) {
            blockers.add("Examination is not published");
        }
        List<ScheduledAssessment> required = assessments
                .findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdOrderByIdAsc(
                        organizationId, yearId, examinationId, gradeId);
        if (required.isEmpty()) {
            blockers.add("No assessments are scheduled for this grade");
        }
        List<ResultSubjectSheet> scoped = lockedSheetsAlready
                ? sheets.findScopeForUpdate(organizationId, yearId, examinationId, gradeId, sectionId)
                : sheets.findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdAndSectionIdOrderByScheduledAssessmentIdAsc(
                        organizationId, yearId, examinationId, gradeId, sectionId);
        Map<Long, ResultSubjectSheet> byAssessment = scoped.stream().collect(Collectors.toMap(
                ResultSubjectSheet::getScheduledAssessmentId, Function.identity(), (left, right) -> left));
        int missing = 0;
        int draft = 0;
        int submitted = 0;
        int returned = 0;
        int approved = 0;
        int incomplete = 0;
        List<ResultPublicationSubjectReadinessResponse> subjectRows = new ArrayList<>();
        for (ScheduledAssessment assessment : required) {
            Subject subject = subjectFor(organizationId, yearId, gradeId, assessment.getGradeLevelSubjectId());
            ResultSubjectSheet sheet = byAssessment.get(assessment.getId());
            ResultSubjectSheetStatus status = sheet == null ? null : sheet.getStatus();
            boolean rosterComplete = false;
            String blocker = null;
            if (sheet == null) {
                missing++;
                blocker = "Subject sheet is missing";
            } else {
                switch (status) {
                    case DRAFT -> draft++;
                    case SUBMITTED -> submitted++;
                    case RETURNED -> returned++;
                    case APPROVED -> approved++;
                }
                if (status != ResultSubjectSheetStatus.APPROVED) {
                    blocker = "Subject sheet is "
                            + status.name().toLowerCase(Locale.ROOT);
                }
                rosterComplete = completeRoster(organizationId, sectionId, assessment, sheet);
                if (!rosterComplete) {
                    incomplete++;
                    blocker = "Eligible-student mark roster is incomplete";
                }
            }
            subjectRows.add(new ResultPublicationSubjectReadinessResponse(assessment.getId(),
                    assessment.getGradeLevelSubjectId(), subject.getId(), subject.getCode(), subject.getName(),
                    status, rosterComplete, blocker));
        }
        if (missing > 0) {
            blockers.add(missing + " subject sheet(s) missing");
        }
        if (draft > 0) {
            blockers.add(draft + " subject sheet(s) still draft");
        }
        if (submitted > 0) {
            blockers.add(submitted + " subject sheet(s) awaiting review");
        }
        if (returned > 0) {
            blockers.add(returned + " subject sheet(s) returned for correction");
        }
        if (incomplete > 0) {
            blockers.add(incomplete + " subject sheet roster(s) incomplete");
        }
        Optional<ResultPublication> publication = publications
                .findByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdAndSectionIdAndStatus(
                        organizationId,
                        yearId,
                        examinationId,
                        gradeId,
                        sectionId,
                        ResultPublicationStatus.PUBLISHED
                );
        return new ResultPublicationReadinessResponse(yearId, examinationId, gradeId, sectionId,
                blockers.isEmpty(), publication.isPresent(), publication.map(ResultPublication::getId).orElse(null),
                required.size(), missing, draft, submitted, returned, approved, incomplete,
                List.copyOf(blockers), List.copyOf(subjectRows));
    }

    private boolean completeRoster(
            long organizationId,
            long sectionId,
            ScheduledAssessment assessment,
            ResultSubjectSheet sheet
    ) {
        Set<Long> eligible = enrollments.findEligibleForAttendanceDate(organizationId,
                assessment.getAcademicYearId(), assessment.getGradeLevelId(), sectionId,
                assessment.getAssessmentDate()).stream().map(StudentEnrollment::getId).collect(Collectors.toSet());
        List<ResultSubjectMark> sheetMarks = marks.findAllBySubjectSheetIdOrderByRollNumberSnapshotAscIdAsc(sheet.getId());
        return !eligible.isEmpty() && sheetMarks.size() == eligible.size()
                && sheetMarks.stream().allMatch(mark -> eligible.contains(mark.getStudentEnrollmentId()) && mark.isComplete());
    }

    private Subject subjectFor(
            long organizationId,
            long yearId,
            long gradeId,
            long gradeSubjectId
    ) {
        GradeLevelSubject assignment = gradeSubjects.findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                        gradeSubjectId, gradeId, yearId, organizationId)
                .orElseThrow(() -> new ResultNotFoundException("Scheduled subject not found"));
        return subjects.findByIdAndAcademicYearIdAndOrganizationId(assignment.getSubjectId(), yearId, organizationId)
                .orElseThrow(() -> new ResultNotFoundException("Subject not found"));
    }

    private PublishedResultDetailResponse detail(ResultPublication publication, long studentUserId) {
        List<ResultSubjectSheet> publicationSheets = publicationSheets(publication);
        List<Long> sheetIds = publicationSheets.stream()
                .map(ResultSubjectSheet::getId)
                .toList();
        List<ResultSubjectMark> studentMarks = sheetIds.isEmpty()
                ? List.of()
                : marks.findAllBySubjectSheetIdInAndStudentUserIdOrderBySubjectSheetIdAsc(
                        sheetIds,
                        studentUserId
                );
        if (studentMarks.isEmpty()) {
            throw new ResultNotFoundException("Published result not found");
        }

        Map<Long, ResultSubjectMark> markBySheet = studentMarks.stream().collect(Collectors.toMap(
                ResultSubjectMark::getSubjectSheetId,
                Function.identity(),
                (first, ignored) -> first
        ));
        Map<Long, ScheduledAssessment> assessmentById = assessments
                .findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdOrderByIdAsc(
                        publication.getOrganizationId(),
                        publication.getAcademicYearId(),
                        publication.getExaminationId(),
                        publication.getGradeLevelId()
                ).stream()
                .collect(Collectors.toMap(
                        ScheduledAssessment::getId,
                        Function.identity()
                ));
        List<PublishedResultSubjectResponse> rows = new ArrayList<>();
        BigDecimal maximum = BigDecimal.ZERO;
        BigDecimal obtained = BigDecimal.ZERO;
        int absent = 0;
        for (ResultSubjectSheet sheet : publicationSheets) {
            ResultSubjectMark mark = markBySheet.get(sheet.getId());
            if (mark == null) {
                continue;
            }

            ScheduledAssessment assessment = assessmentById.get(sheet.getScheduledAssessmentId());
            if (assessment == null) {
                throw new ResultNotFoundException("Published result not found");
            }
            Subject subject = subjectFor(
                    publication.getOrganizationId(),
                    publication.getAcademicYearId(),
                    publication.getGradeLevelId(),
                    sheet.getGradeLevelSubjectId()
            );
            rows.add(new PublishedResultSubjectResponse(
                    subject.getId(),
                    subject.getCode(),
                    subject.getName(),
                    assessment.getMaximumMarks(),
                    mark.getMarksObtained(),
                    Boolean.TRUE.equals(mark.getAbsent())
            ));
            maximum = maximum.add(assessment.getMaximumMarks());
            if (mark.getMarksObtained() != null) {
                obtained = obtained.add(mark.getMarksObtained());
            }
            if (Boolean.TRUE.equals(mark.getAbsent())) {
                absent++;
            }
        }

        AcademicYear year = year(publication);
        Examination examination = examination(publication);
        GradeLevel grade = grade(publication);
        Section section = section(publication);
        ResultSubjectMark first = studentMarks.get(0);
        return new PublishedResultDetailResponse(
                publication.getId(),
                year.getId(),
                year.getName(),
                examination.getId(),
                examination.getName(),
                grade.getId(),
                grade.getName(),
                section.getId(),
                section.getName(),
                publication.getPublishedAt(),
                studentUserId,
                first.getStudentEnrollmentId(),
                first.getRollNumberSnapshot(),
                List.copyOf(rows),
                maximum,
                obtained,
                absent
        );
    }

    private PublishedResultSummaryResponse summary(ResultPublication p) {
        AcademicYear year = year(p);
        Examination examination = examination(p);
        GradeLevel grade = grade(p);
        Section section = section(p);
        return new PublishedResultSummaryResponse(
                p.getId(),
                year.getId(),
                year.getName(),
                examination.getId(),
                examination.getName(),
                grade.getId(),
                grade.getName(),
                section.getId(),
                section.getName(),
                p.getPublishedAt()
        );
    }

    private List<ResultSubjectSheet> publicationSheets(ResultPublication publication) {
        return sheets
                .findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdAndSectionIdOrderByScheduledAssessmentIdAsc(
                        publication.getOrganizationId(),
                        publication.getAcademicYearId(),
                        publication.getExaminationId(),
                        publication.getGradeLevelId(),
                        publication.getSectionId()
                );
    }

    private ResultPublication published(long organizationId, long publicationId) {
        return publications.findByIdAndOrganizationIdAndStatus(
                        publicationId,
                        organizationId,
                        ResultPublicationStatus.PUBLISHED
                )
                .orElseThrow(() -> new ResultNotFoundException("Published result not found"));
    }

    private AcademicYear year(ResultPublication publication) {
        return years.findByIdAndOrganizationId(
                        publication.getAcademicYearId(),
                        publication.getOrganizationId()
                )
                .orElseThrow(() -> new ResultNotFoundException("Published result not found"));
    }

    private Examination examination(ResultPublication publication) {
        return examinations.findByIdAndAcademicYearIdAndOrganizationId(
                        publication.getExaminationId(),
                        publication.getAcademicYearId(),
                        publication.getOrganizationId()
                )
                .orElseThrow(() -> new ResultNotFoundException("Published result not found"));
    }

    private GradeLevel grade(ResultPublication publication) {
        return grades.findByIdAndAcademicYearIdAndOrganizationId(
                        publication.getGradeLevelId(),
                        publication.getAcademicYearId(),
                        publication.getOrganizationId()
                )
                .orElseThrow(() -> new ResultNotFoundException("Published result not found"));
    }

    private Section section(ResultPublication publication) {
        return sections.findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                        publication.getSectionId(),
                        publication.getGradeLevelId(),
                        publication.getAcademicYearId(),
                        publication.getOrganizationId()
                )
                .orElseThrow(() -> new ResultNotFoundException("Published result not found"));
    }

    private void requireGuardian(long organizationId, long guardianId, long studentId) {
        if (!guardianAccess.hasActiveRelationship(organizationId, guardianId, studentId)) {
            throw new ResultNotFoundException("Published result not found");
        }
    }

    private static ResultPublicationResponse response(ResultPublication publication) {
        return new ResultPublicationResponse(
                publication.getId(),
                publication.getAcademicYearId(),
                publication.getExaminationId(),
                publication.getGradeLevelId(),
                publication.getSectionId(),
                publication.getStatus(),
                publication.getPublishedByUserId(),
                publication.getPublishedAt(),
                publication.getVersion()
        );
    }
}
