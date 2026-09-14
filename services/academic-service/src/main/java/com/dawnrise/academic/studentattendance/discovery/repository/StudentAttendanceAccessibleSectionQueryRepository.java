package com.dawnrise.academic.studentattendance.discovery.repository;

import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.studentattendance.discovery.dto.AccessibleStudentAttendanceSectionResponse;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class StudentAttendanceAccessibleSectionQueryRepository {

    private final EntityManager entityManager;

    public StudentAttendanceAccessibleSectionQueryRepository(
            EntityManager entityManager
    ) {
        this.entityManager = entityManager;
    }

    public List<AccessibleStudentAttendanceSectionResponse>
    findAllActiveYearSections(long organizationId) {
        return entityManager.createQuery("""
                        SELECT new com.dawnrise.academic.studentattendance.discovery.dto.AccessibleStudentAttendanceSectionResponse(
                            academicYear.id,
                            academicYear.name,
                            gradeLevel.id,
                            gradeLevel.code,
                            gradeLevel.name,
                            section.id,
                            section.code,
                            section.name
                        )
                        FROM AcademicYear academicYear
                        JOIN GradeLevel gradeLevel
                          ON gradeLevel.organizationId = academicYear.organizationId
                         AND gradeLevel.academicYearId = academicYear.id
                        JOIN Section section
                          ON section.organizationId = gradeLevel.organizationId
                         AND section.academicYearId = gradeLevel.academicYearId
                         AND section.gradeLevelId = gradeLevel.id
                        WHERE academicYear.organizationId = :organizationId
                          AND academicYear.status = :activeStatus
                        ORDER BY academicYear.startDate ASC,
                                 academicYear.id ASC,
                                 gradeLevel.displayOrder ASC,
                                 gradeLevel.id ASC,
                                 section.displayOrder ASC,
                                 section.id ASC
                        """,
                        AccessibleStudentAttendanceSectionResponse.class)
                .setParameter("organizationId", organizationId)
                .setParameter("activeStatus", AcademicYearStatus.ACTIVE)
                .getResultList();
    }

    public List<AccessibleStudentAttendanceSectionResponse>
    findAssignedActiveYearSections(
            long organizationId,
            long teacherUserId
    ) {
        return entityManager.createQuery("""
                        SELECT new com.dawnrise.academic.studentattendance.discovery.dto.AccessibleStudentAttendanceSectionResponse(
                            academicYear.id,
                            academicYear.name,
                            gradeLevel.id,
                            gradeLevel.code,
                            gradeLevel.name,
                            section.id,
                            section.code,
                            section.name
                        )
                        FROM TeacherAssignment assignment
                        JOIN AcademicYear academicYear
                          ON academicYear.organizationId = assignment.organizationId
                         AND academicYear.id = assignment.academicYearId
                        JOIN GradeLevel gradeLevel
                          ON gradeLevel.organizationId = assignment.organizationId
                         AND gradeLevel.academicYearId = assignment.academicYearId
                         AND gradeLevel.id = assignment.gradeLevelId
                        JOIN Section section
                          ON section.organizationId = assignment.organizationId
                         AND section.academicYearId = assignment.academicYearId
                         AND section.gradeLevelId = assignment.gradeLevelId
                         AND section.id = assignment.sectionId
                        WHERE assignment.organizationId = :organizationId
                          AND assignment.teacherUserId = :teacherUserId
                          AND academicYear.status = :activeStatus
                        GROUP BY academicYear.id,
                                 academicYear.name,
                                 academicYear.startDate,
                                 gradeLevel.id,
                                 gradeLevel.code,
                                 gradeLevel.name,
                                 gradeLevel.displayOrder,
                                 section.id,
                                 section.code,
                                 section.name,
                                 section.displayOrder
                        ORDER BY academicYear.startDate ASC,
                                 academicYear.id ASC,
                                 gradeLevel.displayOrder ASC,
                                 gradeLevel.id ASC,
                                 section.displayOrder ASC,
                                 section.id ASC
                        """,
                        AccessibleStudentAttendanceSectionResponse.class)
                .setParameter("organizationId", organizationId)
                .setParameter("teacherUserId", teacherUserId)
                .setParameter("activeStatus", AcademicYearStatus.ACTIVE)
                .getResultList();
    }
}
