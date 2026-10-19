package com.dawnrise.academic.results.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.examination.entity.*;
import com.dawnrise.academic.examination.repository.*;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import com.dawnrise.academic.gradelevelsubject.repository.GradeLevelSubjectRepository;
import com.dawnrise.academic.results.entity.*;
import com.dawnrise.academic.results.enums.ResultSubjectSheetStatus;
import com.dawnrise.academic.results.enums.ResultPublicationStatus;
import com.dawnrise.academic.results.exception.ResultConflictException;
import com.dawnrise.academic.results.exception.ResultNotFoundException;
import com.dawnrise.academic.results.integration.identity.*;
import com.dawnrise.academic.results.repository.*;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentenrollment.repository.StudentEnrollmentRepository;
import com.dawnrise.academic.subject.entity.Subject;
import com.dawnrise.academic.subject.repository.SubjectRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResultPublicationServiceTest {
 @Mock AcademicYearRepository years; @Mock ExaminationRepository examinations;
 @Mock ScheduledAssessmentRepository assessments; @Mock GradeLevelRepository grades;
 @Mock SectionRepository sections; @Mock GradeLevelSubjectRepository gradeSubjects;
 @Mock SubjectRepository subjects; @Mock StudentEnrollmentRepository enrollments;
 @Mock ResultSubjectSheetRepository sheets; @Mock ResultSubjectMarkRepository marks;
 @Mock ResultPublicationRepository publications; @Mock IdentityGuardianAccessClient guardian;
 ResultPublicationService service; AcademicYear year; Examination examination; ScheduledAssessment assessment;
 GradeLevel grade; Section section; GradeLevelSubject mapping; Subject subject;

 @BeforeEach void setup(){
  service=new ResultPublicationService(years,examinations,assessments,grades,sections,gradeSubjects,subjects,enrollments,sheets,marks,publications,guardian);
  year=new AcademicYear(1L,"2026-27",LocalDate.of(2026,4,1),LocalDate.of(2027,3,31)); id(year,2); year.activate();
  examination=new Examination(1,2,"Mid-term"); id(examination,3); examination.publish();
  grade=new GradeLevel(1L,2L,"10","Class 10",1); id(grade,4);
  section=new Section(1L,2L,4L,"A","Section A",1); id(section,7);
  mapping=new GradeLevelSubject(1L,2L,4L,8L,true,1); id(mapping,5);
  subject=new Subject(1L,2L,"MAT","Mathematics",null); id(subject,8);
  assessment=new ScheduledAssessment(1,2,3,4,5,LocalDate.of(2026,10,5),LocalTime.of(9,0),LocalTime.of(10,0),BigDecimal.valueOf(50)); id(assessment,6);
  when(years.findByIdAndOrganizationId(2L,1L)).thenReturn(Optional.of(year));
  when(examinations.findByIdAndAcademicYearIdAndOrganizationId(3L,2L,1L)).thenReturn(Optional.of(examination));
  when(grades.findByIdAndAcademicYearIdAndOrganizationId(4L,2L,1L)).thenReturn(Optional.of(grade));
  when(sections.findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(7L,4L,2L,1L)).thenReturn(Optional.of(section));
 }

 @Test void readinessIsReadyWhenEveryScheduledSheetIsApprovedWithCompleteRoster(){
  ResultSubjectSheet sheet=sheet(ResultSubjectSheetStatus.APPROVED); ResultSubjectMark mark=mark(true);
  scheduled(); when(sheets.findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdAndSectionIdOrderByScheduledAssessmentIdAsc(1L,2L,3L,4L,7L)).thenReturn(List.of(sheet));
  when(enrollments.findEligibleForAttendanceDate(1L,2L,4L,7L,assessment.getAssessmentDate())).thenReturn(List.of(enrollment()));
  when(marks.findAllBySubjectSheetIdOrderByRollNumberSnapshotAscIdAsc(30L)).thenReturn(List.of(mark));
  var result=service.readiness(1,2,3,4,7);
  assertTrue(result.ready()); assertEquals(1,result.approvedCount()); assertTrue(result.blockers().isEmpty());
 }

 @ParameterizedTest @EnumSource(value=ResultSubjectSheetStatus.class,names={"DRAFT","SUBMITTED","RETURNED"})
 void readinessReportsEachBlockingSheetState(ResultSubjectSheetStatus status){
  scheduled(); ResultSubjectSheet sheet=sheet(status);
  when(sheets.findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdAndSectionIdOrderByScheduledAssessmentIdAsc(1L,2L,3L,4L,7L)).thenReturn(List.of(sheet));
  when(enrollments.findEligibleForAttendanceDate(anyLong(),anyLong(),anyLong(),anyLong(),any())).thenReturn(List.of(enrollment()));
  when(marks.findAllBySubjectSheetIdOrderByRollNumberSnapshotAscIdAsc(30L)).thenReturn(List.of(mark(true)));
  var result=service.readiness(1,2,3,4,7);
  assertFalse(result.ready()); assertEquals(status,result.subjects().get(0).sheetStatus());
 }

 @Test void readinessReportsMissingAndNoScheduledAssessments(){
  scheduled(); when(sheets.findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdAndSectionIdOrderByScheduledAssessmentIdAsc(1L,2L,3L,4L,7L)).thenReturn(List.of());
  assertEquals(1,service.readiness(1,2,3,4,7).missingCount());
  when(assessments.findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdOrderByIdAsc(1L,2L,3L,4L)).thenReturn(List.of());
  assertTrue(service.readiness(1,2,3,4,7).blockers().stream().anyMatch(x->x.contains("No assessments")));
 }

 @Test void incompleteRosterBlocksPublication(){
  scheduled(); ResultSubjectSheet sheet=sheet(ResultSubjectSheetStatus.APPROVED);
  when(sheets.findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdAndSectionIdOrderByScheduledAssessmentIdAsc(1L,2L,3L,4L,7L)).thenReturn(List.of(sheet));
  when(enrollments.findEligibleForAttendanceDate(anyLong(),anyLong(),anyLong(),anyLong(),any())).thenReturn(List.of(enrollment()));
  when(marks.findAllBySubjectSheetIdOrderByRollNumberSnapshotAscIdAsc(30L)).thenReturn(List.of(mark(false)));
  var result=service.readiness(1,2,3,4,7); assertFalse(result.ready()); assertEquals(1,result.incompleteRosterCount());
 }

 @Test void parentRelationshipIsRequiredAndIdentityOutageFailsClosed(){
  when(guardian.hasActiveRelationship(1,90,21)).thenReturn(false);
  assertThrows(ResultNotFoundException.class,()->service.listForParent(1,90,21));
  when(guardian.hasActiveRelationship(1,91,21)).thenThrow(new IdentityGuardianAccessException("unavailable",null));
  assertThrows(IdentityGuardianAccessException.class,()->service.listForParent(1,91,21));
 }

 @Test void crossTenantAndMismatchedHierarchyAreRejected(){
  assertThrows(ResultNotFoundException.class,()->service.readiness(99,2,3,4,7));
  when(sections.findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(7L,99L,2L,1L))
    .thenReturn(Optional.empty());
  assertThrows(ResultNotFoundException.class,()->service.readiness(1,2,3,99,7));
 }

 @Test void studentCannotReadAnotherStudentsPublishedResult(){
  ResultPublication publication=publication(); ResultSubjectSheet sheet=sheet(ResultSubjectSheetStatus.APPROVED);
  when(publications.findByIdAndOrganizationIdAndStatus(40L,1L,ResultPublicationStatus.PUBLISHED))
    .thenReturn(Optional.of(publication));
  when(sheets.findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdAndSectionIdOrderByScheduledAssessmentIdAsc(1L,2L,3L,4L,7L))
    .thenReturn(List.of(sheet));
  when(marks.findAllBySubjectSheetIdInAndStudentUserIdOrderBySubjectSheetIdAsc(List.of(30L),22L))
    .thenReturn(List.of());
  assertThrows(ResultNotFoundException.class,()->service.detailForStudent(1,22,40));
 }

 @Test void activeParentCanListOnlyPublishedResultsForLinkedStudent(){
  when(guardian.hasActiveRelationship(1,90,21)).thenReturn(true);
  when(publications.findAllPublishedForStudent(1L,21L)).thenReturn(List.of());
  assertTrue(service.listForParent(1,90,21).isEmpty());
  verify(publications).findAllPublishedForStudent(1L,21L);
 }

 @Test void studentListUsesTheTenantSafeDistinctPublishedQuery(){
  ResultPublication visible=publication();
  when(publications.findAllPublishedForStudent(1L,21L))
    .thenReturn(List.of(visible));
  var result=service.listForStudent(1,21);
  assertEquals(List.of(40L),result.stream().map(item->item.publicationId()).toList());
  verify(publications).findAllPublishedForStudent(1L,21L);
  verifyNoInteractions(sheets,marks);
 }

 @Test void publishedDetailUsesStoredEnrollmentAndRollSnapshots(){
  scheduled(); ResultPublication publication=publication(); ResultSubjectSheet sheet=sheet(ResultSubjectSheetStatus.APPROVED);
  ResultSubjectMark stored=mark(true);
  when(publications.findByIdAndOrganizationIdAndStatus(40L,1L,ResultPublicationStatus.PUBLISHED))
    .thenReturn(Optional.of(publication));
  when(sheets.findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdAndSectionIdOrderByScheduledAssessmentIdAsc(1L,2L,3L,4L,7L))
    .thenReturn(List.of(sheet));
  when(marks.findAllBySubjectSheetIdInAndStudentUserIdOrderBySubjectSheetIdAsc(List.of(30L),21L))
    .thenReturn(List.of(stored));
  var detail=service.detailForStudent(1,21,40);
  assertEquals(11,detail.enrollmentId()); assertEquals("A01",detail.rollNumberSnapshot());
  verifyNoInteractions(enrollments);
 }

 @Test void partialHistoricalResultIncludesOnlySheetsWithStoredStudentMarks(){
  scheduled();
  ScheduledAssessment secondAssessment=new ScheduledAssessment(1,2,3,4,15,
    LocalDate.of(2026,10,6),LocalTime.of(9,0),LocalTime.of(10,0),BigDecimal.valueOf(75));
  id(secondAssessment,16);
  ResultPublication publication=publication();
  ResultSubjectSheet included=sheet(ResultSubjectSheetStatus.APPROVED);
  ResultSubjectSheet excluded=new ResultSubjectSheet(1,2,3,16,4,7,15);
  id(excluded,32);
  when(publications.findByIdAndOrganizationIdAndStatus(40L,1L,ResultPublicationStatus.PUBLISHED))
    .thenReturn(Optional.of(publication));
  when(publications.findAllPublishedForStudent(1L,21L))
    .thenReturn(List.of(publication));
  when(sheets.findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdAndSectionIdOrderByScheduledAssessmentIdAsc(1L,2L,3L,4L,7L))
    .thenReturn(List.of(included,excluded));
  when(marks.findAllBySubjectSheetIdInAndStudentUserIdOrderBySubjectSheetIdAsc(List.of(30L,32L),21L))
    .thenReturn(List.of(mark(true)));
  when(assessments.findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdOrderByIdAsc(1L,2L,3L,4L))
    .thenReturn(List.of(assessment,secondAssessment));
  when(gradeSubjects.findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(5L,4L,2L,1L))
    .thenReturn(Optional.of(mapping));
  when(subjects.findByIdAndAcademicYearIdAndOrganizationId(8L,2L,1L)).thenReturn(Optional.of(subject));

  var summaries=service.listForStudent(1,21);
  var detail=service.detailForStudent(1,21,40);

  assertEquals(List.of(40L),summaries.stream().map(item->item.publicationId()).toList());
  assertEquals(1,detail.subjects().size());
  assertEquals(BigDecimal.valueOf(50),detail.totalMaximumMarks());
  assertEquals(BigDecimal.TEN,detail.totalObtainedMarks());
 }

 @Test void repeatedPublishReturnsExistingPublication(){
  ResultPublication publication=new ResultPublication(1,2,3,4,7,100); id(publication,40);
  ReflectionTestUtils.setField(publication,"version",0L);
  when(examinations.findForUpdate(3L,2L,1L)).thenReturn(Optional.of(examination));
  when(publications.findScopeForUpdate(1L,2L,3L,4L,7L)).thenReturn(Optional.of(publication));
  assertEquals(40,service.publish(1,100,2,3,4,7).id()); verify(publications,never()).saveAndFlush(any());
 }

 @Test void bulkPublicationDetailsResolveAssignmentsAndSubjectsWithoutPerSheetLookups(){
  scheduled(); ResultPublication publication=publication(); ResultSubjectSheet sheet=sheet(ResultSubjectSheetStatus.APPROVED);
  when(publications.findByIdAndOrganizationIdAndStatus(40L,1L,ResultPublicationStatus.PUBLISHED))
    .thenReturn(Optional.of(publication));
  when(sheets.findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdAndSectionIdOrderByScheduledAssessmentIdAsc(1L,2L,3L,4L,7L))
    .thenReturn(List.of(sheet));
  when(marks.findAllBySubjectSheetIdInOrderBySubjectSheetIdAscRollNumberSnapshotAscIdAsc(List.of(30L)))
    .thenReturn(List.of(mark(true)));
  when(gradeSubjects.findAllByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndIdIn(1L,2L,4L,Set.of(5L)))
    .thenReturn(List.of(mapping));
  when(subjects.findAllByOrganizationIdAndAcademicYearIdAndIdIn(1L,2L,Set.of(8L)))
    .thenReturn(List.of(subject));

  var details=service.detailsForPublication(1,40);

  assertEquals(1,details.size()); assertEquals("Mathematics",details.getFirst().subjects().getFirst().subjectName());
  verify(gradeSubjects,never()).findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(anyLong(),anyLong(),anyLong(),anyLong());
  verify(subjects,never()).findByIdAndAcademicYearIdAndOrganizationId(anyLong(),anyLong(),anyLong());
 }

 @Test void withdrawnPublicationIsNeitherAlreadyPublishedNorIdempotentlyReturned(){
  scheduled();
  ResultPublication withdrawn=publication();
  ReflectionTestUtils.setField(withdrawn,"status",ResultPublicationStatus.WITHDRAWN);
  when(publications.findByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdAndSectionIdAndStatus(
    1L,2L,3L,4L,7L,ResultPublicationStatus.PUBLISHED)).thenReturn(Optional.empty());
  when(sheets.findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdAndSectionIdOrderByScheduledAssessmentIdAsc(
    1L,2L,3L,4L,7L)).thenReturn(List.of());
  assertFalse(service.readiness(1,2,3,4,7).alreadyPublished());

  when(examinations.findForUpdate(3L,2L,1L)).thenReturn(Optional.of(examination));
  when(publications.findScopeForUpdate(1L,2L,3L,4L,7L)).thenReturn(Optional.of(withdrawn));
  assertThrows(ResultConflictException.class,()->service.publish(1,100,2,3,4,7));
 }

 private void scheduled(){
  when(assessments.findAllByOrganizationIdAndAcademicYearIdAndExaminationIdAndGradeLevelIdOrderByIdAsc(1L,2L,3L,4L)).thenReturn(List.of(assessment));
  when(gradeSubjects.findByIdAndGradeLevelIdAndAcademicYearIdAndOrganizationId(5L,4L,2L,1L)).thenReturn(Optional.of(mapping));
  when(subjects.findByIdAndAcademicYearIdAndOrganizationId(8L,2L,1L)).thenReturn(Optional.of(subject));
 }
 private ResultSubjectSheet sheet(ResultSubjectSheetStatus status){ResultSubjectSheet s=new ResultSubjectSheet(1,2,3,6,4,7,5);id(s,30);ReflectionTestUtils.setField(s,"version",0L);if(status!=ResultSubjectSheetStatus.DRAFT){s.submit(50);if(status==ResultSubjectSheetStatus.RETURNED)s.returnForCorrection(51,"Correct");if(status==ResultSubjectSheetStatus.APPROVED)s.approve(51);}return s;}
 private StudentEnrollment enrollment(){StudentEnrollment e=new StudentEnrollment(1L,2L,4L,7L,21L,"A01",LocalDate.of(2026,4,1));id(e,11);return e;}
 private ResultSubjectMark mark(boolean complete){ResultSubjectMark m=new ResultSubjectMark(1,2,4,7,30,11,21,"A01");id(m,31);if(complete)m.record(false,BigDecimal.TEN);return m;}
 private ResultPublication publication(){ResultPublication p=new ResultPublication(1,2,3,4,7,100);id(p,40);ReflectionTestUtils.setField(p,"version",0L);return p;}
 private static void id(Object entity,long value){ReflectionTestUtils.setField(entity,"id",value);}
}
