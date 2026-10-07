package com.dawnrise.academic.results.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.examination.entity.Examination;
import com.dawnrise.academic.examination.entity.ScheduledAssessment;
import com.dawnrise.academic.examination.enums.ExaminationStatus;
import com.dawnrise.academic.examination.repository.ExaminationRepository;
import com.dawnrise.academic.examination.repository.ScheduledAssessmentRepository;
import com.dawnrise.academic.results.dto.ResultSheetVersionRequest;
import com.dawnrise.academic.results.dto.ResultSubjectMarkEntryRequest;
import com.dawnrise.academic.results.dto.ResultSubjectMarkResponse;
import com.dawnrise.academic.results.dto.ResultSubjectSheetResponse;
import com.dawnrise.academic.results.dto.ReturnResultSubjectSheetRequest;
import com.dawnrise.academic.results.dto.SaveResultSubjectMarksRequest;
import com.dawnrise.academic.results.entity.ResultSubjectMark;
import com.dawnrise.academic.results.entity.ResultSubjectSheet;
import com.dawnrise.academic.results.exception.InvalidResultException;
import com.dawnrise.academic.results.exception.ResultConflictException;
import com.dawnrise.academic.results.exception.ResultNotFoundException;
import com.dawnrise.academic.results.repository.ResultSubjectMarkRepository;
import com.dawnrise.academic.results.repository.ResultSubjectSheetRepository;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentenrollment.integration.identity.BatchStudentEnrollmentEligibilityResponse;
import com.dawnrise.academic.studentenrollment.integration.identity.IdentityStudentEligibilityClient;
import com.dawnrise.academic.studentenrollment.integration.identity.IdentityStudentEligibilityException;
import com.dawnrise.academic.studentenrollment.repository.StudentEnrollmentRepository;
import com.dawnrise.academic.teacherassignment.enums.TeacherAssignmentType;
import com.dawnrise.academic.teacherassignment.repository.TeacherAssignmentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ResultSubjectSheetService {
    private static final int IDENTITY_BATCH_LIMIT = 100;
    private static final Logger log = LoggerFactory.getLogger(
            ResultSubjectSheetService.class
    );

    private final AcademicYearRepository academicYears;
    private final ExaminationRepository examinations;
    private final ScheduledAssessmentRepository assessments;
    private final SectionRepository sections;
    private final StudentEnrollmentRepository enrollments;
    private final TeacherAssignmentRepository teacherAssignments;
    private final ResultSubjectSheetRepository sheets;
    private final ResultSubjectMarkRepository marks;
    private final IdentityStudentEligibilityClient identityStudents;

    public ResultSubjectSheetService(AcademicYearRepository academicYears,
                                     ExaminationRepository examinations,
                                     ScheduledAssessmentRepository assessments,
                                     SectionRepository sections,
                                     StudentEnrollmentRepository enrollments,
                                     TeacherAssignmentRepository teacherAssignments,
                                     ResultSubjectSheetRepository sheets,
                                     ResultSubjectMarkRepository marks,
                                     IdentityStudentEligibilityClient identityStudents) {
        this.academicYears = academicYears;
        this.examinations = examinations;
        this.assessments = assessments;
        this.sections = sections;
        this.enrollments = enrollments;
        this.teacherAssignments = teacherAssignments;
        this.sheets = sheets;
        this.marks = marks;
        this.identityStudents = identityStudents;
    }

    public ResultSubjectSheetResponse getOrCreate(long organizationId,
                                                  long actorUserId,
                                                  long academicYearId,
                                                  long examinationId,
                                                  long scheduledAssessmentId,
                                                  long sectionId) {
        ScheduledAssessment assessment = assessment(organizationId, academicYearId,
                examinationId, scheduledAssessmentId);
        validateOpenYear(organizationId, academicYearId);
        validatePublishedExamination(organizationId, academicYearId, examinationId);
        validateSection(organizationId, academicYearId, assessment.getGradeLevelId(), sectionId);
        requireSubjectTeacher(organizationId, actorUserId, assessment, sectionId);

        ResultSubjectSheet sheet = sheets
                .findByOrganizationIdAndAcademicYearIdAndExaminationIdAndScheduledAssessmentIdAndSectionId(
                        organizationId, academicYearId, examinationId,
                        scheduledAssessmentId, sectionId)
                .orElseGet(() -> createSheet(organizationId, assessment, sectionId));
        return response(sheet, assessment);
    }

    @Transactional(readOnly = true)
    public ResultSubjectSheetResponse get(long organizationId, long actorUserId,
                                          long sheetId) {
        ResultSubjectSheet sheet = findSheet(organizationId, sheetId);
        ScheduledAssessment assessment = assessment(organizationId,
                sheet.getAcademicYearId(), sheet.getExaminationId(),
                sheet.getScheduledAssessmentId());
        requireSheetAccess(organizationId, actorUserId, sheet);
        return response(sheet, assessment);
    }

    public ResultSubjectSheetResponse saveMarks(long organizationId,
                                                long actorUserId,
                                                long sheetId,
                                                SaveResultSubjectMarksRequest request) {
        ResultSubjectSheet sheet = lockedSheet(organizationId, sheetId);
        matchVersion(sheet.getVersion(), request.sheetVersion());
        ScheduledAssessment assessment = assessment(organizationId,
                sheet.getAcademicYearId(), sheet.getExaminationId(),
                sheet.getScheduledAssessmentId());
        validateOpenYear(organizationId, sheet.getAcademicYearId());
        requireSubjectTeacher(organizationId, actorUserId, sheet);
        sheet.requireEditable();

        List<ResultSubjectMark> existingMarks =
                marks.findAllBySubjectSheetIdForUpdate(sheet.getId());
        validateAndApplyMarks(existingMarks, request.marks(), assessment.getMaximumMarks());
        marks.saveAllAndFlush(existingMarks);
        return response(sheet, assessment);
    }

    public ResultSubjectSheetResponse submit(long organizationId, long actorUserId,
                                             long sheetId,
                                             ResultSheetVersionRequest request) {
        ResultSubjectSheet sheet = lockedSheet(organizationId, sheetId);
        matchVersion(sheet.getVersion(), request.version());
        ScheduledAssessment assessment = assessment(organizationId,
                sheet.getAcademicYearId(), sheet.getExaminationId(),
                sheet.getScheduledAssessmentId());
        validateOpenYear(organizationId, sheet.getAcademicYearId());
        requireSubjectTeacher(organizationId, actorUserId, sheet);
        List<ResultSubjectMark> existingMarks =
                marks.findAllBySubjectSheetIdForUpdate(sheet.getId());
        if (existingMarks.isEmpty()) {
            throw new ResultConflictException("Subject sheet has no eligible students");
        }
        if (existingMarks.stream().anyMatch(mark -> !mark.isComplete())) {
            throw new ResultConflictException("All marks must be entered before submission");
        }
        sheet.submit(actorUserId);
        return response(sheets.saveAndFlush(sheet), assessment);
    }

    public ResultSubjectSheetResponse returnForCorrection(long organizationId,
                                                          long actorUserId,
                                                          long sheetId,
                                                          ReturnResultSubjectSheetRequest request) {
        ResultSubjectSheet sheet = lockedSheet(organizationId, sheetId);
        matchVersion(sheet.getVersion(), request.version());
        ScheduledAssessment assessment = assessment(organizationId,
                sheet.getAcademicYearId(), sheet.getExaminationId(),
                sheet.getScheduledAssessmentId());
        validateOpenYear(organizationId, sheet.getAcademicYearId());
        requireClassTeacher(organizationId, actorUserId, sheet);
        sheet.returnForCorrection(actorUserId, request.note());
        return response(sheets.saveAndFlush(sheet), assessment);
    }

    public ResultSubjectSheetResponse approve(long organizationId,
                                              long actorUserId,
                                              long sheetId,
                                              ResultSheetVersionRequest request) {
        ResultSubjectSheet sheet = lockedSheet(organizationId, sheetId);
        matchVersion(sheet.getVersion(), request.version());
        ScheduledAssessment assessment = assessment(organizationId,
                sheet.getAcademicYearId(), sheet.getExaminationId(),
                sheet.getScheduledAssessmentId());
        validateOpenYear(organizationId, sheet.getAcademicYearId());
        requireClassTeacher(organizationId, actorUserId, sheet);
        sheet.approve(actorUserId);
        return response(sheets.saveAndFlush(sheet), assessment);
    }

    private ResultSubjectSheet createSheet(long organizationId,
                                           ScheduledAssessment assessment,
                                           long sectionId) {
        List<StudentEnrollment> roster = enrollments.findEligibleForAttendanceDate(
                organizationId, assessment.getAcademicYearId(),
                assessment.getGradeLevelId(), sectionId,
                assessment.getAssessmentDate());
        ResultSubjectSheet sheet = sheets.saveAndFlush(new ResultSubjectSheet(
                organizationId, assessment.getAcademicYearId(),
                assessment.getExaminationId(), assessment.getId(),
                assessment.getGradeLevelId(), sectionId,
                assessment.getGradeLevelSubjectId()));
        List<ResultSubjectMark> createdMarks = roster.stream()
                .map(enrollment -> new ResultSubjectMark(organizationId,
                        assessment.getAcademicYearId(), assessment.getGradeLevelId(),
                        sectionId, sheet.getId(), enrollment.getId(),
                        enrollment.getStudentUserId(), enrollment.getRollNumber()))
                .toList();
        marks.saveAllAndFlush(createdMarks);
        return sheet;
    }

    private ScheduledAssessment assessment(long organizationId, long academicYearId,
                                           long examinationId, long assessmentId) {
        return assessments.findByIdAndExaminationIdAndAcademicYearIdAndOrganizationId(
                        assessmentId, examinationId, academicYearId, organizationId)
                .orElseThrow(() -> new ResultNotFoundException("Scheduled assessment not found"));
    }

    private ResultSubjectSheet findSheet(long organizationId, long sheetId) {
        return sheets.findByIdAndOrganizationId(sheetId, organizationId)
                .orElseThrow(() -> new ResultNotFoundException("Subject sheet not found"));
    }

    private ResultSubjectSheet lockedSheet(long organizationId, long sheetId) {
        return sheets.findByIdAndOrganizationIdForUpdate(sheetId, organizationId)
                .orElseThrow(() -> new ResultNotFoundException("Subject sheet not found"));
    }

    private void validateOpenYear(long organizationId, long academicYearId) {
        AcademicYear year = academicYears.findByIdAndOrganizationId(academicYearId, organizationId)
                .orElseThrow(() -> new ResultNotFoundException("Academic year not found"));
        if (year.getStatus() != AcademicYearStatus.ACTIVE) {
            throw new ResultConflictException("Results can be changed only in an active academic year");
        }
    }

    private void validatePublishedExamination(long organizationId,
                                              long academicYearId,
                                              long examinationId) {
        Examination examination = examinations
                .findByIdAndAcademicYearIdAndOrganizationId(
                        examinationId, academicYearId, organizationId)
                .orElseThrow(() -> new ResultNotFoundException("Examination not found"));
        if (examination.getStatus() != ExaminationStatus.PUBLISHED) {
            throw new ResultConflictException("Marks can be entered only for a published examination");
        }
    }

    private void validateSection(long organizationId, long academicYearId,
                                 long gradeLevelId, long sectionId) {
        if (sections.findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                sectionId, gradeLevelId, academicYearId, organizationId).isEmpty()) {
            throw new ResultNotFoundException("Section not found");
        }
    }

    private void requireSheetAccess(long organizationId, long actorUserId,
                                    ResultSubjectSheet sheet) {
        if (isSubjectTeacher(organizationId, actorUserId, sheet)
                || isClassTeacher(organizationId, actorUserId, sheet)) {
            return;
        }
        throw new AccessDeniedException("Access Denied");
    }

    private void requireSubjectTeacher(long organizationId, long actorUserId,
                                       ScheduledAssessment assessment,
                                       long sectionId) {
        if (!isSubjectTeacher(organizationId, actorUserId, assessment, sectionId)) {
            throw new AccessDeniedException("Access Denied");
        }
    }

    private void requireSubjectTeacher(long organizationId, long actorUserId,
                                       ResultSubjectSheet sheet) {
        if (!isSubjectTeacher(organizationId, actorUserId, sheet)) {
            throw new AccessDeniedException("Access Denied");
        }
    }

    private void requireClassTeacher(long organizationId, long actorUserId,
                                     ResultSubjectSheet sheet) {
        if (!isClassTeacher(organizationId, actorUserId, sheet)) {
            throw new AccessDeniedException("Access Denied");
        }
    }

    private boolean isSubjectTeacher(long organizationId, long actorUserId,
                                     ResultSubjectSheet sheet) {
        return teacherAssignments
                .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndGradeLevelSubjectIdAndTeacherUserIdAndAssignmentType(
                        organizationId, sheet.getAcademicYearId(),
                        sheet.getGradeLevelId(), sheet.getSectionId(),
                        sheet.getGradeLevelSubjectId(), actorUserId,
                        TeacherAssignmentType.SUBJECT_TEACHER);
    }

    private boolean isSubjectTeacher(long organizationId, long actorUserId,
                                     ScheduledAssessment assessment,
                                     long sectionId) {
        return teacherAssignments
                .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndGradeLevelSubjectIdAndTeacherUserIdAndAssignmentType(
                        organizationId, assessment.getAcademicYearId(),
                        assessment.getGradeLevelId(), sectionId,
                        assessment.getGradeLevelSubjectId(), actorUserId,
                        TeacherAssignmentType.SUBJECT_TEACHER);
    }

    private boolean isClassTeacher(long organizationId, long actorUserId,
                                   ResultSubjectSheet sheet) {
        return teacherAssignments
                .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndTeacherUserIdAndAssignmentType(
                        organizationId, sheet.getAcademicYearId(),
                        sheet.getGradeLevelId(), sheet.getSectionId(),
                        actorUserId, TeacherAssignmentType.CLASS_TEACHER);
    }

    private static void validateAndApplyMarks(List<ResultSubjectMark> existingMarks,
                                              List<ResultSubjectMarkEntryRequest> requestedMarks,
                                              BigDecimal maximumMarks) {
        if (existingMarks.size() != requestedMarks.size()) {
            throw new InvalidResultException("Request must include every eligible student exactly once");
        }
        Map<Long, ResultSubjectMark> marksByEnrollment = existingMarks.stream()
                .collect(Collectors.toMap(ResultSubjectMark::getStudentEnrollmentId,
                        Function.identity()));
        Set<Long> seen = new HashSet<>();
        Map<ResultSubjectMark, ResultSubjectMarkEntryRequest> validated = new HashMap<>();
        for (ResultSubjectMarkEntryRequest item : requestedMarks) {
            if (!seen.add(item.studentEnrollmentId())) {
                throw new InvalidResultException("Duplicate student mark entry");
            }
            ResultSubjectMark mark = marksByEnrollment.get(item.studentEnrollmentId());
            if (mark == null) {
                throw new InvalidResultException("Student is not eligible for this subject sheet");
            }
            matchVersion(mark.getVersion(), item.version());
            validateMark(item, maximumMarks);
            validated.put(mark, item);
        }
        if (validated.size() != existingMarks.size()) {
            throw new InvalidResultException("Request must include every eligible student exactly once");
        }
        validated.forEach((mark, item) -> mark.record(item.absent(), item.marksObtained()));
    }

    private static void validateMark(ResultSubjectMarkEntryRequest item,
                                     BigDecimal maximumMarks) {
        if (Boolean.TRUE.equals(item.absent())) {
            if (item.marksObtained() != null) {
                throw new InvalidResultException("Absent students cannot have marks");
            }
            return;
        }
        if (item.marksObtained() == null) {
            throw new InvalidResultException("Marks are required for present students");
        }
        if (item.marksObtained().compareTo(maximumMarks) > 0) {
            throw new InvalidResultException("Marks cannot exceed the scheduled assessment maximum");
        }
    }

    private static void matchVersion(Long actual, Long expected) {
        if (!Objects.equals(actual, expected)) {
            throw new ResultConflictException("Record was modified by another request");
        }
    }

    private ResultSubjectSheetResponse response(ResultSubjectSheet sheet,
                                                ScheduledAssessment assessment) {
        List<ResultSubjectMark> subjectMarks = marks
                .findAllBySubjectSheetIdOrderByRollNumberSnapshotAscIdAsc(sheet.getId());
        Map<Long, String> displayNames = resolveStudentDisplayNames(
                sheet.getOrganizationId(),
                subjectMarks
        );
        List<ResultSubjectMarkResponse> markResponses = subjectMarks.stream()
                .map(mark -> new ResultSubjectMarkResponse(mark.getId(),
                        mark.getStudentEnrollmentId(), mark.getStudentUserId(),
                        displayNames.get(mark.getStudentUserId()),
                        mark.getRollNumberSnapshot(), mark.getAbsent(),
                        mark.getMarksObtained(), mark.getVersion(),
                        mark.getCreatedAt(), mark.getUpdatedAt()))
                .toList();
        return new ResultSubjectSheetResponse(sheet.getId(), sheet.getAcademicYearId(),
                sheet.getExaminationId(), sheet.getScheduledAssessmentId(),
                sheet.getGradeLevelId(), sheet.getSectionId(),
                sheet.getGradeLevelSubjectId(), assessment.getMaximumMarks(),
                assessment.getAssessmentDate(), sheet.getStatus(),
                sheet.getSubmittedByUserId(), sheet.getSubmittedAt(),
                sheet.getReviewedByUserId(), sheet.getReviewedAt(),
                sheet.getReviewNote(), sheet.getVersion(), markResponses,
                sheet.getCreatedAt(), sheet.getUpdatedAt());
    }

    private Map<Long, String> resolveStudentDisplayNames(
            long organizationId,
            List<ResultSubjectMark> subjectMarks
    ) {
        Set<Long> requestedUserIds = subjectMarks.stream()
                .map(ResultSubjectMark::getStudentUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (requestedUserIds.isEmpty()) {
            return Map.of();
        }

        try {
            List<Long> sortedUserIds = requestedUserIds.stream()
                    .sorted()
                    .toList();
            Map<Long, String> displayNames = new HashMap<>();
            for (int start = 0;
                 start < sortedUserIds.size();
                 start += IDENTITY_BATCH_LIMIT) {
                List<Long> batchUserIds = sortedUserIds.subList(
                        start,
                        Math.min(
                                start + IDENTITY_BATCH_LIMIT,
                                sortedUserIds.size()
                        )
                );
                BatchStudentEnrollmentEligibilityResponse response =
                        identityStudents.checkBatch(
                                organizationId,
                                batchUserIds
                        );
                if (response == null || response.results() == null) {
                    continue;
                }
                response.results().stream()
                        .filter(Objects::nonNull)
                        .filter(result -> Objects.equals(
                                result.organizationId(),
                                organizationId
                        ))
                        .filter(result -> requestedUserIds.contains(
                                result.userId()
                        ))
                        .filter(result -> result.displayName() != null
                                && !result.displayName().isBlank())
                        .forEach(result -> displayNames.putIfAbsent(
                                result.userId(),
                                result.displayName().trim()
                        ));
            }
            return Map.copyOf(displayNames);
        } catch (IdentityStudentEligibilityException
                 | IllegalStateException exception) {
            log.warn(
                    "Student display names could not be enriched for organization {}",
                    organizationId,
                    exception
            );
            return Map.of();
        }
    }
}
