package com.dawnrise.academic.examination.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.examination.dto.CreateScheduledAssessmentRequest;
import com.dawnrise.academic.examination.dto.UpdateScheduledAssessmentRequest;
import com.dawnrise.academic.examination.dto.VersionRequest;
import com.dawnrise.academic.examination.entity.Examination;
import com.dawnrise.academic.examination.entity.ScheduledAssessment;
import com.dawnrise.academic.examination.exception.ExaminationConflictException;
import com.dawnrise.academic.examination.exception.InvalidExaminationException;
import com.dawnrise.academic.examination.repository.ExaminationRepository;
import com.dawnrise.academic.examination.repository.ScheduledAssessmentRepository;
import com.dawnrise.academic.gradelevelsubject.repository.GradeLevelSubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExaminationServiceTest {
    @Mock AcademicYearRepository years;
    @Mock GradeLevelSubjectRepository gradeSubjects;
    @Mock ExaminationRepository examinations;
    @Mock ScheduledAssessmentRepository assessments;
    ExaminationService service;
    AcademicYear year;
    Examination examination;

    @BeforeEach
    void setUp() {
        service = new ExaminationService(years, gradeSubjects, examinations, assessments);
        year = new AcademicYear(1L, "2026-27",
                LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31));
        examination = new Examination(1, 2, "Mid-term");
        ReflectionTestUtils.setField(examination, "id", 3L);
        ReflectionTestUtils.setField(examination, "version", 0L);
    }

    @Test
    void viewerCannotSeeDraftButManagerCan() {
        when(years.findByIdAndOrganizationId(2L, 1L)).thenReturn(Optional.of(year));
        when(examinations.findAllByOrganizationIdAndAcademicYearIdOrderByIdAsc(1L, 2L))
                .thenReturn(List.of(examination));
        assertTrue(service.list(1, 2, false).isEmpty());
        assertEquals(1, service.list(1, 2, true).size());
    }

    @Test
    void cannotPublishEmptyExamination() {
        when(years.findByIdAndOrganizationId(2L, 1L)).thenReturn(Optional.of(year));
        when(examinations.findForUpdate(3L, 2L, 1L)).thenReturn(Optional.of(examination));
        assertThrows(ExaminationConflictException.class,
                () -> service.publish(1, 2, 3, new VersionRequest(0L)));
        verify(examinations, never()).saveAndFlush(any());
    }

    @Test
    void cannotScheduleGradeSubjectFromAnotherTenantOrYear() {
        when(years.findByIdAndOrganizationId(2L, 1L)).thenReturn(Optional.of(year));
        when(examinations.findForUpdate(3L, 2L, 1L)).thenReturn(Optional.of(examination));
        CreateScheduledAssessmentRequest request = new CreateScheduledAssessmentRequest(
                4L, 5L, LocalDate.of(2026, 10, 5),
                LocalTime.of(9, 0), LocalTime.of(10, 0), BigDecimal.valueOf(50));
        assertThrows(InvalidExaminationException.class,
                () -> service.addAssessment(1, 2, 3, request));
        verify(gradeSubjects).findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                5L, 4L, 2L, 1L);
        verify(assessments, never()).saveAndFlush(any());
    }

    @Test
    void cannotUpdateAssessmentWhenStoredGradeSubjectIsNoLongerTenantScoped() {
        ScheduledAssessment assessment = new ScheduledAssessment(
                1, 2, 3, 4, 5, LocalDate.of(2026, 10, 5),
                LocalTime.of(9, 0), LocalTime.of(10, 0), BigDecimal.valueOf(50));
        ReflectionTestUtils.setField(assessment, "id", 6L);
        ReflectionTestUtils.setField(assessment, "version", 0L);
        when(years.findByIdAndOrganizationId(2L, 1L)).thenReturn(Optional.of(year));
        when(examinations.findForUpdate(3L, 2L, 1L)).thenReturn(Optional.of(examination));
        when(assessments.findByIdAndExaminationIdAndAcademicYearIdAndOrganizationId(
                6L, 3L, 2L, 1L)).thenReturn(Optional.of(assessment));
        UpdateScheduledAssessmentRequest request = new UpdateScheduledAssessmentRequest(
                LocalDate.of(2026, 10, 6),
                LocalTime.of(9, 0), LocalTime.of(10, 0), BigDecimal.valueOf(50), 0L);

        assertThrows(InvalidExaminationException.class,
                () -> service.updateAssessment(1, 2, 3, 6, request));

        verify(gradeSubjects).findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                5L, 4L, 2L, 1L);
        verify(assessments, never()).saveAndFlush(any());
    }
}
