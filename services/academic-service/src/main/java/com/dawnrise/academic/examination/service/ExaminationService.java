package com.dawnrise.academic.examination.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.examination.dto.CreateExaminationRequest;
import com.dawnrise.academic.examination.dto.CreateScheduledAssessmentRequest;
import com.dawnrise.academic.examination.dto.ExaminationResponse;
import com.dawnrise.academic.examination.dto.ScheduledAssessmentResponse;
import com.dawnrise.academic.examination.dto.UpdateExaminationRequest;
import com.dawnrise.academic.examination.dto.UpdateScheduledAssessmentRequest;
import com.dawnrise.academic.examination.dto.VersionRequest;
import com.dawnrise.academic.examination.entity.Examination;
import com.dawnrise.academic.examination.entity.ScheduledAssessment;
import com.dawnrise.academic.examination.enums.ExaminationStatus;
import com.dawnrise.academic.examination.exception.ExaminationConflictException;
import com.dawnrise.academic.examination.exception.ExaminationNotFoundException;
import com.dawnrise.academic.examination.exception.InvalidExaminationException;
import com.dawnrise.academic.examination.repository.ExaminationRepository;
import com.dawnrise.academic.examination.repository.ScheduledAssessmentRepository;
import com.dawnrise.academic.gradelevelsubject.repository.GradeLevelSubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class ExaminationService {
    private final AcademicYearRepository academicYears;
    private final GradeLevelSubjectRepository gradeSubjects;
    private final ExaminationRepository examinations;
    private final ScheduledAssessmentRepository assessments;

    public ExaminationService(AcademicYearRepository academicYears,
                              GradeLevelSubjectRepository gradeSubjects,
                              ExaminationRepository examinations,
                              ScheduledAssessmentRepository assessments) {
        this.academicYears = academicYears;
        this.gradeSubjects = gradeSubjects;
        this.examinations = examinations;
        this.assessments = assessments;
    }

    public ExaminationResponse create(long organizationId, long yearId,
                                      CreateExaminationRequest request) {
        modifiableYear(organizationId, yearId);
        String name = normalizedName(request.name());
        if (examinations.existsByOrganizationIdAndAcademicYearIdAndNameIgnoreCase(
                organizationId, yearId, name)) {
            throw new ExaminationConflictException("An examination with this name already exists");
        }
        return response(examinations.saveAndFlush(new Examination(organizationId, yearId, name)));
    }

    @Transactional(readOnly = true)
    public List<ExaminationResponse> list(long organizationId, long yearId,
                                          boolean canManage) {
        year(organizationId, yearId);
        return examinations.findAllByOrganizationIdAndAcademicYearIdOrderByIdAsc(
                        organizationId, yearId).stream()
                .filter(e -> visible(e, canManage))
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExaminationResponse get(long organizationId, long yearId,
                                    long examinationId, boolean canManage) {
        Examination examination = find(organizationId, yearId, examinationId);
        if (!visible(examination, canManage)) {
            throw new ExaminationNotFoundException("Examination not found");
        }
        return response(examination);
    }

    public ExaminationResponse rename(long organizationId, long yearId,
                                       long examinationId,
                                       UpdateExaminationRequest request) {
        modifiableYear(organizationId, yearId);
        Examination examination = locked(organizationId, yearId, examinationId);
        matchVersion(examination.getVersion(), request.version());
        String name = normalizedName(request.name());
        if (examinations.existsByOrganizationIdAndAcademicYearIdAndNameIgnoreCaseAndIdNot(
                organizationId, yearId, name, examinationId)) {
            throw new ExaminationConflictException("An examination with this name already exists");
        }
        examination.rename(name);
        return response(examinations.saveAndFlush(examination));
    }

    public ExaminationResponse publish(long organizationId, long yearId,
                                        long examinationId, VersionRequest request) {
        modifiableYear(organizationId, yearId);
        Examination examination = locked(organizationId, yearId, examinationId);
        matchVersion(examination.getVersion(), request.version());
        examination.requireDraft();
        if (!assessments.existsByOrganizationIdAndAcademicYearIdAndExaminationId(
                organizationId, yearId, examinationId)) {
            throw new ExaminationConflictException("Add at least one scheduled assessment before publishing");
        }
        examination.publish();
        return response(examinations.saveAndFlush(examination));
    }

    public ExaminationResponse cancel(long organizationId, long yearId,
                                       long examinationId, VersionRequest request) {
        modifiableYear(organizationId, yearId);
        Examination examination = locked(organizationId, yearId, examinationId);
        matchVersion(examination.getVersion(), request.version());
        examination.cancel();
        return response(examinations.saveAndFlush(examination));
    }

    public ExaminationResponse addAssessment(long organizationId, long yearId,
                                              long examinationId,
                                              CreateScheduledAssessmentRequest request) {
        AcademicYear year = modifiableYear(organizationId, yearId);
        Examination examination = locked(organizationId, yearId, examinationId);
        examination.requireDraft();
        validateAssessment(year, request.assessmentDate(), request.startTime(), request.endTime());
        validateGradeSubject(organizationId, yearId, request.gradeLevelId(),
                request.gradeLevelSubjectId());
        if (assessments.existsByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelSubjectId(
                organizationId, yearId, examinationId, request.gradeLevelSubjectId())) {
            throw new ExaminationConflictException("This grade subject is already scheduled in the examination");
        }
        checkTimeConflict(organizationId, yearId, examinationId, request.gradeLevelId(),
                request.assessmentDate(), request.startTime(), request.endTime(), null);
        assessments.saveAndFlush(new ScheduledAssessment(
                organizationId, yearId, examinationId, request.gradeLevelId(),
                request.gradeLevelSubjectId(), request.assessmentDate(),
                request.startTime(), request.endTime(), request.maximumMarks()));
        return response(examination);
    }

    public ExaminationResponse updateAssessment(long organizationId, long yearId,
                                                 long examinationId, long assessmentId,
                                                 UpdateScheduledAssessmentRequest request) {
        AcademicYear year = modifiableYear(organizationId, yearId);
        Examination examination = locked(organizationId, yearId, examinationId);
        examination.requireDraft();
        ScheduledAssessment assessment = assessment(organizationId, yearId,
                examinationId, assessmentId);
        matchVersion(assessment.getVersion(), request.version());
        validateGradeSubject(organizationId, yearId, assessment.getGradeLevelId(),
                assessment.getGradeLevelSubjectId());
        validateAssessment(year, request.assessmentDate(), request.startTime(), request.endTime());
        checkTimeConflict(organizationId, yearId, examinationId,
                assessment.getGradeLevelId(), request.assessmentDate(),
                request.startTime(), request.endTime(), assessmentId);
        assessment.reschedule(request.assessmentDate(), request.startTime(),
                request.endTime(), request.maximumMarks());
        assessments.saveAndFlush(assessment);
        return response(examination);
    }

    public void removeAssessment(long organizationId, long yearId,
                                  long examinationId, long assessmentId,
                                  long version) {
        modifiableYear(organizationId, yearId);
        Examination examination = locked(organizationId, yearId, examinationId);
        examination.requireDraft();
        ScheduledAssessment assessment = assessment(organizationId, yearId,
                examinationId, assessmentId);
        matchVersion(assessment.getVersion(), version);
        assessments.delete(assessment);
        assessments.flush();
    }

    private AcademicYear year(long organizationId, long yearId) {
        return academicYears.findByIdAndOrganizationId(yearId, organizationId)
                .orElseThrow(() -> new ExaminationNotFoundException("Academic year not found"));
    }

    private AcademicYear modifiableYear(long organizationId, long yearId) {
        AcademicYear year = year(organizationId, yearId);
        if (year.getStatus() != AcademicYearStatus.PLANNED
                && year.getStatus() != AcademicYearStatus.ACTIVE) {
            throw new ExaminationConflictException(
                    "Examinations can be changed only in planned or active academic years");
        }
        return year;
    }

    private Examination find(long organizationId, long yearId, long id) {
        return examinations.findByIdAndAcademicYearIdAndOrganizationId(id, yearId, organizationId)
                .orElseThrow(() -> new ExaminationNotFoundException("Examination not found"));
    }

    private Examination locked(long organizationId, long yearId, long id) {
        return examinations.findForUpdate(id, yearId, organizationId)
                .orElseThrow(() -> new ExaminationNotFoundException("Examination not found"));
    }

    private ScheduledAssessment assessment(long organizationId, long yearId,
                                            long examinationId, long assessmentId) {
        return assessments.findByIdAndExaminationIdAndAcademicYearIdAndOrganizationId(
                        assessmentId, examinationId, yearId, organizationId)
                .orElseThrow(() -> new ExaminationNotFoundException("Scheduled assessment not found"));
    }

    private void validateGradeSubject(long organizationId, long yearId,
                                      long gradeLevelId, long gradeLevelSubjectId) {
        gradeSubjects.findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                        gradeLevelSubjectId, gradeLevelId, yearId, organizationId)
                .orElseThrow(() -> new InvalidExaminationException(
                        "Grade subject does not belong to this organization, year and grade"));
    }

    private static void matchVersion(Long actual, Long expected) {
        if (!Objects.equals(actual, expected)) {
            throw new ExaminationConflictException("Record was modified by another request");
        }
    }

    private static String normalizedName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidExaminationException("Examination name is required");
        }
        return name.trim();
    }

    private static void validateAssessment(AcademicYear year, LocalDate date,
                                           LocalTime start, LocalTime end) {
        if (date == null || date.isBefore(year.getStartDate())
                || date.isAfter(year.getEndDate())) {
            throw new InvalidExaminationException("Assessment date must be within the academic year");
        }
        if (start == null || end == null || !start.isBefore(end)) {
            throw new InvalidExaminationException("Start time must be before end time");
        }
    }

    private void checkTimeConflict(long organizationId, long yearId,
                                   long examinationId, long gradeLevelId,
                                   LocalDate date, LocalTime start,
                                   LocalTime end, Long excludeId) {
        if (assessments.hasTimeConflict(organizationId, yearId, examinationId,
                gradeLevelId, date, start, end, excludeId)) {
            throw new ExaminationConflictException(
                    "Another subject for this grade is scheduled at the same time");
        }
    }

    private static boolean visible(Examination examination, boolean canManage) {
        return canManage || examination.getStatus() == ExaminationStatus.PUBLISHED
                || (examination.getStatus() == ExaminationStatus.CANCELLED
                    && examination.getPublishedAt() != null);
    }

    private ExaminationResponse response(Examination examination) {
        List<ScheduledAssessmentResponse> items = assessments
                .findAllByOrganizationIdAndAcademicYearIdAndExaminationIdOrderByAssessmentDateAscStartTimeAscIdAsc(
                        examination.getOrganizationId(), examination.getAcademicYearId(),
                        examination.getId()).stream()
                .map(a -> new ScheduledAssessmentResponse(
                        a.getId(), a.getGradeLevelId(), a.getGradeLevelSubjectId(),
                        a.getAssessmentDate(), a.getStartTime(), a.getEndTime(),
                        a.getMaximumMarks(), a.getVersion(), a.getCreatedAt(),
                        a.getUpdatedAt()))
                .toList();
        return new ExaminationResponse(examination.getId(), examination.getAcademicYearId(),
                examination.getName(), examination.getStatus(), examination.getPublishedAt(),
                examination.getVersion(), items, examination.getCreatedAt(), examination.getUpdatedAt());
    }
}
