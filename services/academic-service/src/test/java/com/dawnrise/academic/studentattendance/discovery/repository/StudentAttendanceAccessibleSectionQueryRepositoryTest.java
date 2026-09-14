package com.dawnrise.academic.studentattendance.discovery.repository;

import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.studentattendance.discovery.dto.AccessibleStudentAttendanceSectionResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StudentAttendanceAccessibleSectionQueryRepositoryTest {

    private EntityManager entityManager;
    private TypedQuery<AccessibleStudentAttendanceSectionResponse> query;
    private StudentAttendanceAccessibleSectionQueryRepository repository;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        query = mock(TypedQuery.class);
        when(entityManager.createQuery(
                anyString(),
                eq(AccessibleStudentAttendanceSectionResponse.class)
        )).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        repository = new StudentAttendanceAccessibleSectionQueryRepository(
                entityManager
        );
    }

    @Test
    void leadershipQueryIsTenantActiveYearScopedAndDeterministicallyOrdered() {
        repository.findAllActiveYearSections(7L);

        String jpql = capturedJpql();
        assertThat(jpql)
                .contains("academicYear.organizationId = :organizationId")
                .contains("academicYear.status = :activeStatus")
                .contains("JOIN GradeLevel gradeLevel")
                .contains("JOIN Section section")
                .contains("ORDER BY academicYear.startDate ASC")
                .contains("academicYear.id ASC")
                .contains("gradeLevel.displayOrder ASC")
                .contains("gradeLevel.id ASC")
                .contains("section.displayOrder ASC")
                .contains("section.id ASC");
        verify(query).setParameter("organizationId", 7L);
        verify(query).setParameter("activeStatus", AcademicYearStatus.ACTIVE);
    }

    @Test
    void teacherQueryIsTenantTeacherActiveYearScopedAndGroupedForDeduplication() {
        repository.findAssignedActiveYearSections(7L, 11L);

        String jpql = capturedJpql();
        assertThat(jpql)
                .contains("FROM TeacherAssignment assignment")
                .contains("assignment.organizationId = :organizationId")
                .contains("assignment.teacherUserId = :teacherUserId")
                .contains("academicYear.status = :activeStatus")
                .contains("assignment.sectionId")
                .contains("GROUP BY academicYear.id")
                .contains("gradeLevel.displayOrder")
                .contains("section.displayOrder")
                .contains("ORDER BY academicYear.startDate ASC")
                .contains("section.id ASC");
        verify(query).setParameter("organizationId", 7L);
        verify(query).setParameter("teacherUserId", 11L);
        verify(query).setParameter("activeStatus", AcademicYearStatus.ACTIVE);
    }

    private String capturedJpql() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(
                captor.capture(),
                eq(AccessibleStudentAttendanceSectionResponse.class)
        );
        return captor.getValue();
    }
}
