package com.dawnrise.academic.results.repository;

import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.examination.enums.ExaminationStatus;
import com.dawnrise.academic.teacherassignment.enums.TeacherAssignmentType;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ResultWorklistQueryRepository {
    private final EntityManager entityManager;

    public ResultWorklistQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<ResultWorklistProjection> findSubjectTeacherTasks(
            long organizationId,
            long actorUserId) {
        return entityManager.createQuery("""
                SELECT new com.dawnrise.academic.results.repository.ResultWorklistProjection(
                    'MARK_ENTRY',
                    academicYear.id,
                    academicYear.name,
                    examination.id,
                    examination.name,
                    assessment.id,
                    assessment.assessmentDate,
                    assessment.maximumMarks,
                    gradeLevel.id,
                    gradeLevel.code,
                    gradeLevel.name,
                    section.id,
                    section.code,
                    section.name,
                    gradeSubject.id,
                    subject.id,
                    subject.code,
                    subject.name,
                    sheet.id,
                    sheet.status,
                    sheet.version,
                    CASE WHEN sheet.id IS NULL THEN (
                        SELECT COUNT(enrollment.id)
                        FROM StudentEnrollment enrollment
                        WHERE enrollment.organizationId = assignment.organizationId
                          AND enrollment.academicYearId = assignment.academicYearId
                          AND enrollment.gradeLevelId = assignment.gradeLevelId
                          AND enrollment.sectionId = assignment.sectionId
                          AND enrollment.enrolledOn <= assessment.assessmentDate
                          AND (enrollment.endedOn IS NULL OR enrollment.endedOn >= assessment.assessmentDate)
                    ) ELSE (
                        SELECT COUNT(mark.id)
                        FROM ResultSubjectMark mark
                        WHERE mark.subjectSheetId = sheet.id
                    ) END,
                    CASE WHEN sheet.id IS NULL THEN (
                        SELECT COUNT(enrollment.id)
                        FROM StudentEnrollment enrollment
                        WHERE enrollment.organizationId = assignment.organizationId
                          AND enrollment.academicYearId = assignment.academicYearId
                          AND enrollment.gradeLevelId = assignment.gradeLevelId
                          AND enrollment.sectionId = assignment.sectionId
                          AND enrollment.id < 0
                    ) ELSE (
                        SELECT COUNT(mark.id)
                        FROM ResultSubjectMark mark
                        WHERE mark.subjectSheetId = sheet.id
                          AND (mark.absent = true OR mark.marksObtained IS NOT NULL)
                    ) END
                )
                FROM TeacherAssignment assignment
                JOIN AcademicYear academicYear
                  ON academicYear.organizationId = assignment.organizationId
                 AND academicYear.id = assignment.academicYearId
                JOIN ScheduledAssessment assessment
                  ON assessment.organizationId = assignment.organizationId
                 AND assessment.academicYearId = assignment.academicYearId
                 AND assessment.gradeLevelId = assignment.gradeLevelId
                 AND assessment.gradeLevelSubjectId = assignment.gradeLevelSubjectId
                JOIN Examination examination
                  ON examination.organizationId = assessment.organizationId
                 AND examination.academicYearId = assessment.academicYearId
                 AND examination.id = assessment.examinationId
                JOIN GradeLevel gradeLevel
                  ON gradeLevel.organizationId = assignment.organizationId
                 AND gradeLevel.academicYearId = assignment.academicYearId
                 AND gradeLevel.id = assignment.gradeLevelId
                JOIN Section section
                  ON section.organizationId = assignment.organizationId
                 AND section.academicYearId = assignment.academicYearId
                 AND section.gradeLevelId = assignment.gradeLevelId
                 AND section.id = assignment.sectionId
                JOIN GradeLevelSubject gradeSubject
                  ON gradeSubject.organizationId = assignment.organizationId
                 AND gradeSubject.academicYearId = assignment.academicYearId
                 AND gradeSubject.gradeLevelId = assignment.gradeLevelId
                 AND gradeSubject.id = assignment.gradeLevelSubjectId
                JOIN Subject subject
                  ON subject.organizationId = gradeSubject.organizationId
                 AND subject.academicYearId = gradeSubject.academicYearId
                 AND subject.id = gradeSubject.subjectId
                LEFT JOIN ResultSubjectSheet sheet
                  ON sheet.organizationId = assignment.organizationId
                 AND sheet.academicYearId = assignment.academicYearId
                 AND sheet.examinationId = examination.id
                 AND sheet.scheduledAssessmentId = assessment.id
                 AND sheet.sectionId = assignment.sectionId
                WHERE assignment.organizationId = :organizationId
                  AND assignment.teacherUserId = :actorUserId
                  AND assignment.assignmentType = :subjectTeacher
                  AND academicYear.status = :activeYear
                  AND examination.status = :publishedExamination
                ORDER BY assessment.assessmentDate ASC,
                         examination.id ASC,
                         gradeLevel.displayOrder ASC,
                         section.displayOrder ASC,
                         gradeSubject.displayOrder ASC,
                         assessment.id ASC
                """, ResultWorklistProjection.class)
                .setParameter("organizationId", organizationId)
                .setParameter("actorUserId", actorUserId)
                .setParameter("subjectTeacher", TeacherAssignmentType.SUBJECT_TEACHER)
                .setParameter("activeYear", AcademicYearStatus.ACTIVE)
                .setParameter("publishedExamination", ExaminationStatus.PUBLISHED)
                .getResultList();
    }

    public List<ResultWorklistProjection> findClassTeacherTasks(
            long organizationId,
            long actorUserId) {
        return entityManager.createQuery("""
                SELECT new com.dawnrise.academic.results.repository.ResultWorklistProjection(
                    'REVIEW',
                    academicYear.id,
                    academicYear.name,
                    examination.id,
                    examination.name,
                    assessment.id,
                    assessment.assessmentDate,
                    assessment.maximumMarks,
                    gradeLevel.id,
                    gradeLevel.code,
                    gradeLevel.name,
                    section.id,
                    section.code,
                    section.name,
                    gradeSubject.id,
                    subject.id,
                    subject.code,
                    subject.name,
                    sheet.id,
                    sheet.status,
                    sheet.version,
                    CASE WHEN sheet.id IS NULL THEN (
                        SELECT COUNT(enrollment.id)
                        FROM StudentEnrollment enrollment
                        WHERE enrollment.organizationId = assignment.organizationId
                          AND enrollment.academicYearId = assignment.academicYearId
                          AND enrollment.gradeLevelId = assignment.gradeLevelId
                          AND enrollment.sectionId = assignment.sectionId
                          AND enrollment.enrolledOn <= assessment.assessmentDate
                          AND (enrollment.endedOn IS NULL OR enrollment.endedOn >= assessment.assessmentDate)
                    ) ELSE (
                        SELECT COUNT(mark.id)
                        FROM ResultSubjectMark mark
                        WHERE mark.subjectSheetId = sheet.id
                    ) END,
                    CASE WHEN sheet.id IS NULL THEN (
                        SELECT COUNT(enrollment.id)
                        FROM StudentEnrollment enrollment
                        WHERE enrollment.organizationId = assignment.organizationId
                          AND enrollment.academicYearId = assignment.academicYearId
                          AND enrollment.gradeLevelId = assignment.gradeLevelId
                          AND enrollment.sectionId = assignment.sectionId
                          AND enrollment.id < 0
                    ) ELSE (
                        SELECT COUNT(mark.id)
                        FROM ResultSubjectMark mark
                        WHERE mark.subjectSheetId = sheet.id
                          AND (mark.absent = true OR mark.marksObtained IS NOT NULL)
                    ) END
                )
                FROM TeacherAssignment assignment
                JOIN AcademicYear academicYear
                  ON academicYear.organizationId = assignment.organizationId
                 AND academicYear.id = assignment.academicYearId
                JOIN ScheduledAssessment assessment
                  ON assessment.organizationId = assignment.organizationId
                 AND assessment.academicYearId = assignment.academicYearId
                 AND assessment.gradeLevelId = assignment.gradeLevelId
                JOIN Examination examination
                  ON examination.organizationId = assessment.organizationId
                 AND examination.academicYearId = assessment.academicYearId
                 AND examination.id = assessment.examinationId
                JOIN GradeLevel gradeLevel
                  ON gradeLevel.organizationId = assignment.organizationId
                 AND gradeLevel.academicYearId = assignment.academicYearId
                 AND gradeLevel.id = assignment.gradeLevelId
                JOIN Section section
                  ON section.organizationId = assignment.organizationId
                 AND section.academicYearId = assignment.academicYearId
                 AND section.gradeLevelId = assignment.gradeLevelId
                 AND section.id = assignment.sectionId
                JOIN GradeLevelSubject gradeSubject
                  ON gradeSubject.organizationId = assessment.organizationId
                 AND gradeSubject.academicYearId = assessment.academicYearId
                 AND gradeSubject.gradeLevelId = assessment.gradeLevelId
                 AND gradeSubject.id = assessment.gradeLevelSubjectId
                JOIN Subject subject
                  ON subject.organizationId = gradeSubject.organizationId
                 AND subject.academicYearId = gradeSubject.academicYearId
                 AND subject.id = gradeSubject.subjectId
                LEFT JOIN ResultSubjectSheet sheet
                  ON sheet.organizationId = assignment.organizationId
                 AND sheet.academicYearId = assignment.academicYearId
                 AND sheet.examinationId = examination.id
                 AND sheet.scheduledAssessmentId = assessment.id
                 AND sheet.sectionId = assignment.sectionId
                WHERE assignment.organizationId = :organizationId
                  AND assignment.teacherUserId = :actorUserId
                  AND assignment.assignmentType = :classTeacher
                  AND academicYear.status = :activeYear
                  AND examination.status = :publishedExamination
                ORDER BY assessment.assessmentDate ASC,
                         examination.id ASC,
                         gradeLevel.displayOrder ASC,
                         section.displayOrder ASC,
                         gradeSubject.displayOrder ASC,
                         assessment.id ASC
                """, ResultWorklistProjection.class)
                .setParameter("organizationId", organizationId)
                .setParameter("actorUserId", actorUserId)
                .setParameter("classTeacher", TeacherAssignmentType.CLASS_TEACHER)
                .setParameter("activeYear", AcademicYearStatus.ACTIVE)
                .setParameter("publishedExamination", ExaminationStatus.PUBLISHED)
                .getResultList();
    }
}
