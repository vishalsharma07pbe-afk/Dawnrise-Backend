package com.dawnrise.academic.results.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.examination.entity.Examination;
import com.dawnrise.academic.examination.entity.ScheduledAssessment;
import com.dawnrise.academic.examination.repository.ExaminationRepository;
import com.dawnrise.academic.examination.repository.ScheduledAssessmentRepository;
import com.dawnrise.academic.results.dto.ResultSheetVersionRequest;
import com.dawnrise.academic.results.dto.ResultSubjectMarkEntryRequest;
import com.dawnrise.academic.results.dto.ReturnResultSubjectSheetRequest;
import com.dawnrise.academic.results.dto.SaveResultSubjectMarksRequest;
import com.dawnrise.academic.results.entity.ResultSubjectMark;
import com.dawnrise.academic.results.entity.ResultSubjectSheet;
import com.dawnrise.academic.results.enums.ResultSubjectSheetStatus;
import com.dawnrise.academic.results.exception.InvalidResultException;
import com.dawnrise.academic.results.repository.ResultSubjectMarkRepository;
import com.dawnrise.academic.results.repository.ResultSubjectSheetRepository;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentenrollment.integration.identity.BatchStudentEnrollmentEligibilityResponse;
import com.dawnrise.academic.studentenrollment.integration.identity.IdentityStudentEligibilityClient;
import com.dawnrise.academic.studentenrollment.integration.identity.IdentityStudentEligibilityException;
import com.dawnrise.academic.studentenrollment.integration.identity.StudentEnrollmentEligibilityResponse;
import com.dawnrise.academic.studentenrollment.repository.StudentEnrollmentRepository;
import com.dawnrise.academic.teacherassignment.enums.TeacherAssignmentType;
import com.dawnrise.academic.teacherassignment.repository.TeacherAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultSubjectSheetServiceTest {
    @Mock AcademicYearRepository academicYears;
    @Mock ExaminationRepository examinations;
    @Mock ScheduledAssessmentRepository assessments;
    @Mock SectionRepository sections;
    @Mock StudentEnrollmentRepository enrollments;
    @Mock TeacherAssignmentRepository teacherAssignments;
    @Mock ResultSubjectSheetRepository sheets;
    @Mock ResultSubjectMarkRepository marks;
    @Mock IdentityStudentEligibilityClient identityStudents;

    ResultSubjectSheetService service;
    AcademicYear year;
    Examination examination;
    ScheduledAssessment assessment;

    @BeforeEach
    void setUp() {
        service = new ResultSubjectSheetService(academicYears, examinations,
                assessments, sections, enrollments, teacherAssignments, sheets, marks,
                identityStudents);
        year = new AcademicYear(1L, "2026-27",
                LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31));
        year.activate();
        examination = new Examination(1, 2, "Mid-term");
        ReflectionTestUtils.setField(examination, "id", 3L);
        ReflectionTestUtils.setField(examination, "version", 0L);
        examination.publish();
        assessment = new ScheduledAssessment(1, 2, 3, 4, 5,
                LocalDate.of(2026, 10, 5), LocalTime.of(9, 0),
                LocalTime.of(10, 0), BigDecimal.valueOf(50));
        ReflectionTestUtils.setField(assessment, "id", 6L);
        ReflectionTestUtils.setField(assessment, "version", 0L);
    }

    @Test
    void subjectTeacherCreatesSheetFromAssessmentDateRoster() {
        StudentEnrollment first = enrollment(11, 21, "A001");
        StudentEnrollment second = enrollment(12, 22, "A002");
        when(assessments.findByIdAndExaminationIdAndAcademicYearIdAndOrganizationId(
                6L, 3L, 2L, 1L)).thenReturn(Optional.of(assessment));
        when(academicYears.findByIdAndOrganizationId(2L, 1L)).thenReturn(Optional.of(year));
        when(examinations.findByIdAndAcademicYearIdAndOrganizationId(3L, 2L, 1L))
                .thenReturn(Optional.of(examination));
        when(sections.findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(
                7L, 4L, 2L, 1L)).thenReturn(Optional.of(new com.dawnrise.academic.section.entity.Section()));
        when(teacherAssignments.existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndGradeLevelSubjectIdAndTeacherUserIdAndAssignmentType(
                1L, 2L, 4L, 7L, 5L, 100L, TeacherAssignmentType.SUBJECT_TEACHER))
                .thenReturn(true);
        when(enrollments.findEligibleForAttendanceDate(1L, 2L, 4L, 7L,
                LocalDate.of(2026, 10, 5))).thenReturn(List.of(first, second));
        when(sheets.saveAndFlush(any(ResultSubjectSheet.class))).thenAnswer(invocation -> {
            ResultSubjectSheet sheet = invocation.getArgument(0);
            ReflectionTestUtils.setField(sheet, "id", 30L);
            ReflectionTestUtils.setField(sheet, "version", 0L);
            return sheet;
        });
        when(marks.findAllBySubjectSheetIdOrderByRollNumberSnapshotAscIdAsc(30L))
                .thenReturn(List.of(mark(31, 30, 11, 21, "A001"),
                        mark(32, 30, 12, 22, "A002")));

        var response = service.getOrCreate(1, 100, 2, 3, 6, 7);

        assertEquals(30L, response.id());
        assertEquals(2, response.marks().size());
        verify(enrollments).findEligibleForAttendanceDate(1L, 2L, 4L, 7L,
                LocalDate.of(2026, 10, 5));
    }

    @Test
    void saveMarksValidatesAllRowsBeforeMutating() {
        ResultSubjectSheet sheet = sheet(ResultSubjectSheetStatus.DRAFT);
        ResultSubjectMark mark = mark(31, 30, 11, 21, "A001");
        when(sheets.findByIdAndOrganizationIdForUpdate(30L, 1L))
                .thenReturn(Optional.of(sheet));
        when(assessments.findByIdAndExaminationIdAndAcademicYearIdAndOrganizationId(
                6L, 3L, 2L, 1L)).thenReturn(Optional.of(assessment));
        when(academicYears.findByIdAndOrganizationId(2L, 1L)).thenReturn(Optional.of(year));
        when(teacherAssignments.existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndGradeLevelSubjectIdAndTeacherUserIdAndAssignmentType(
                1L, 2L, 4L, 7L, 5L, 100L, TeacherAssignmentType.SUBJECT_TEACHER))
                .thenReturn(true);
        when(marks.findAllBySubjectSheetIdForUpdate(30L)).thenReturn(List.of(mark));
        SaveResultSubjectMarksRequest request = new SaveResultSubjectMarksRequest(
                0L,
                List.of(new ResultSubjectMarkEntryRequest(11L, false,
                        BigDecimal.valueOf(55), 0L)));

        assertThrows(InvalidResultException.class,
                () -> service.saveMarks(1, 100, 30, request));

        verify(marks, never()).saveAllAndFlush(any());
        assertEquals(null, mark.getMarksObtained());
    }

    @Test
    void saveMarksAllowsZeroForPresentAndNullMarksForAbsent() {
        ResultSubjectSheet sheet = sheet(ResultSubjectSheetStatus.DRAFT);
        ResultSubjectMark present = mark(31, 30, 11, 21, "A001");
        ResultSubjectMark absent = mark(32, 30, 12, 22, "A002");
        when(sheets.findByIdAndOrganizationIdForUpdate(30L, 1L))
                .thenReturn(Optional.of(sheet));
        when(assessments.findByIdAndExaminationIdAndAcademicYearIdAndOrganizationId(
                6L, 3L, 2L, 1L)).thenReturn(Optional.of(assessment));
        when(academicYears.findByIdAndOrganizationId(2L, 1L)).thenReturn(Optional.of(year));
        when(teacherAssignments.existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndGradeLevelSubjectIdAndTeacherUserIdAndAssignmentType(
                1L, 2L, 4L, 7L, 5L, 100L, TeacherAssignmentType.SUBJECT_TEACHER))
                .thenReturn(true);
        when(marks.findAllBySubjectSheetIdForUpdate(30L))
                .thenReturn(List.of(present, absent));
        when(marks.findAllBySubjectSheetIdOrderByRollNumberSnapshotAscIdAsc(30L))
                .thenReturn(List.of(present, absent));
        SaveResultSubjectMarksRequest request = new SaveResultSubjectMarksRequest(
                0L,
                List.of(
                        new ResultSubjectMarkEntryRequest(11L, false,
                                BigDecimal.ZERO, 0L),
                        new ResultSubjectMarkEntryRequest(12L, true,
                                null, 0L)
                ));

        var response = service.saveMarks(1, 100, 30, request);

        assertEquals(BigDecimal.ZERO, response.marks().get(0).marksObtained());
        assertEquals(false, response.marks().get(0).absent());
        assertEquals(null, response.marks().get(1).marksObtained());
        assertEquals(true, response.marks().get(1).absent());
        verify(marks).saveAllAndFlush(List.of(present, absent));
    }

    @Test
    void classTeacherApprovesSubmittedSheet() {
        ResultSubjectSheet sheet = sheet(ResultSubjectSheetStatus.DRAFT);
        sheet.submit(100);
        ReflectionTestUtils.setField(sheet, "version", 1L);
        when(sheets.findByIdAndOrganizationIdForUpdate(30L, 1L))
                .thenReturn(Optional.of(sheet));
        when(assessments.findByIdAndExaminationIdAndAcademicYearIdAndOrganizationId(
                6L, 3L, 2L, 1L)).thenReturn(Optional.of(assessment));
        when(academicYears.findByIdAndOrganizationId(2L, 1L)).thenReturn(Optional.of(year));
        when(teacherAssignments.existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndTeacherUserIdAndAssignmentType(
                1L, 2L, 4L, 7L, 200L, TeacherAssignmentType.CLASS_TEACHER))
                .thenReturn(true);
        when(sheets.saveAndFlush(sheet)).thenReturn(sheet);
        when(marks.findAllBySubjectSheetIdOrderByRollNumberSnapshotAscIdAsc(30L))
                .thenReturn(List.of());

        var response = service.approve(1, 200, 30, new ResultSheetVersionRequest(1L));

        assertEquals(ResultSubjectSheetStatus.APPROVED, response.status());
        assertEquals(200L, response.reviewedByUserId());
    }

    @Test
    void subjectTeacherCannotReturnSubmittedSheet() {
        ResultSubjectSheet sheet = sheet(ResultSubjectSheetStatus.DRAFT);
        sheet.submit(100);
        ReflectionTestUtils.setField(sheet, "version", 1L);
        when(sheets.findByIdAndOrganizationIdForUpdate(30L, 1L))
                .thenReturn(Optional.of(sheet));
        when(assessments.findByIdAndExaminationIdAndAcademicYearIdAndOrganizationId(
                6L, 3L, 2L, 1L)).thenReturn(Optional.of(assessment));
        when(academicYears.findByIdAndOrganizationId(2L, 1L)).thenReturn(Optional.of(year));

        assertThrows(AccessDeniedException.class,
                () -> service.returnForCorrection(1, 100, 30,
                        new ReturnResultSubjectSheetRequest(1L, "Fix totals")));

        verify(sheets, never()).saveAndFlush(any());
    }

    @Test
    void enrichesStudentNamesWithOneOrganizationScopedBatchLookup() {
        ResultSubjectMark first = mark(31, 30, 11, 21, "A001");
        ResultSubjectMark second = mark(32, 30, 12, 22, "A002");
        stubReadableSheet(1L, List.of(first, second));
        when(identityStudents.checkBatch(1L, List.of(21L, 22L)))
                .thenReturn(new BatchStudentEnrollmentEligibilityResponse(List.of(
                        identityStudent(22L, 1L, "  Bea Singh  "),
                        identityStudent(21L, 1L, "Arun Das")
                )));

        var response = service.get(1L, 100L, 30L);

        assertEquals("Arun Das", response.marks().get(0).studentDisplayName());
        assertEquals("Bea Singh", response.marks().get(1).studentDisplayName());
        verify(identityStudents).checkBatch(1L, List.of(21L, 22L));
    }

    @Test
    void ignoresIdentityRowsFromAnotherOrganization() {
        ResultSubjectMark mark = mark(31, 30, 11, 21, "A001");
        stubReadableSheet(1L, List.of(mark));
        when(identityStudents.checkBatch(1L, List.of(21L)))
                .thenReturn(new BatchStudentEnrollmentEligibilityResponse(List.of(
                        identityStudent(21L, 2L, "Other tenant student")
                )));

        var response = service.get(1L, 100L, 30L);

        assertNull(response.marks().get(0).studentDisplayName());
        verify(identityStudents).checkBatch(1L, List.of(21L));
    }

    @Test
    void returnsSheetWithoutNamesWhenIdentityServiceIsUnavailable() {
        ResultSubjectMark mark = mark(31, 30, 11, 21, "A001");
        stubReadableSheet(1L, List.of(mark));
        when(identityStudents.checkBatch(1L, List.of(21L)))
                .thenThrow(new IdentityStudentEligibilityException(
                        "Identity service unavailable",
                        null
                ));

        var response = service.get(1L, 100L, 30L);

        assertEquals(30L, response.id());
        assertNull(response.marks().get(0).studentDisplayName());
    }

    private void stubReadableSheet(
            long organizationId,
            List<ResultSubjectMark> subjectMarks
    ) {
        ResultSubjectSheet sheet = sheet(ResultSubjectSheetStatus.DRAFT);
        when(sheets.findByIdAndOrganizationId(30L, organizationId))
                .thenReturn(Optional.of(sheet));
        when(assessments.findByIdAndExaminationIdAndAcademicYearIdAndOrganizationId(
                6L, 3L, 2L, organizationId)).thenReturn(Optional.of(assessment));
        when(teacherAssignments.existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndGradeLevelSubjectIdAndTeacherUserIdAndAssignmentType(
                organizationId, 2L, 4L, 7L, 5L, 100L,
                TeacherAssignmentType.SUBJECT_TEACHER)).thenReturn(true);
        when(marks.findAllBySubjectSheetIdOrderByRollNumberSnapshotAscIdAsc(30L))
                .thenReturn(subjectMarks);
    }

    private static StudentEnrollmentEligibilityResponse identityStudent(
            long userId,
            long organizationId,
            String displayName
    ) {
        return new StudentEnrollmentEligibilityResponse(
                userId,
                organizationId,
                displayName,
                true,
                null
        );
    }

    private ResultSubjectSheet sheet(ResultSubjectSheetStatus status) {
        ResultSubjectSheet sheet = new ResultSubjectSheet(1, 2, 3, 6, 4, 7, 5);
        ReflectionTestUtils.setField(sheet, "id", 30L);
        ReflectionTestUtils.setField(sheet, "version", 0L);
        if (status == ResultSubjectSheetStatus.SUBMITTED) {
            sheet.submit(100);
            ReflectionTestUtils.setField(sheet, "version", 1L);
        }
        return sheet;
    }

    private static StudentEnrollment enrollment(long enrollmentId, long studentUserId,
                                                String rollNumber) {
        StudentEnrollment enrollment = new StudentEnrollment(1L, 2L, 4L, 7L,
                studentUserId, rollNumber, LocalDate.of(2026, 4, 2));
        ReflectionTestUtils.setField(enrollment, "id", enrollmentId);
        ReflectionTestUtils.setField(enrollment, "version", 0L);
        return enrollment;
    }

    private static ResultSubjectMark mark(long markId, long sheetId,
                                          long enrollmentId, long studentUserId,
                                          String rollNumber) {
        ResultSubjectMark mark = new ResultSubjectMark(1, 2, 4, 7, sheetId,
                enrollmentId, studentUserId, rollNumber);
        ReflectionTestUtils.setField(mark, "id", markId);
        ReflectionTestUtils.setField(mark, "version", 0L);
        return mark;
    }
}
