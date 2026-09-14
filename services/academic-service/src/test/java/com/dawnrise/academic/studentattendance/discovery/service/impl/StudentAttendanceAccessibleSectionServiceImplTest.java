package com.dawnrise.academic.studentattendance.discovery.service.impl;

import com.dawnrise.academic.studentattendance.discovery.dto.AccessibleStudentAttendanceSectionResponse;
import com.dawnrise.academic.studentattendance.discovery.repository.StudentAttendanceAccessibleSectionQueryRepository;
import com.dawnrise.academic.studentattendance.recording.exception.InvalidStudentAttendanceRecordingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StudentAttendanceAccessibleSectionServiceImplTest {

    private StudentAttendanceAccessibleSectionQueryRepository repository;
    private StudentAttendanceAccessibleSectionServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(StudentAttendanceAccessibleSectionQueryRepository.class);
        service = new StudentAttendanceAccessibleSectionServiceImpl(repository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void leadershipReceivesAllActiveYearSectionsInRepositoryOrder() {
        authenticate("ROLE_PRINCIPAL");
        List<AccessibleStudentAttendanceSectionResponse> rows = List.of(
                row(3L, 4L, "CLASS_1", 5L, "A"),
                row(3L, 4L, "CLASS_1", 6L, "B"),
                row(3L, 7L, "CLASS_2", 8L, "A")
        );
        when(repository.findAllActiveYearSections(7L)).thenReturn(rows);

        List<AccessibleStudentAttendanceSectionResponse> result =
                service.listAccessibleSections(7L, 11L);

        assertThat(result).containsExactlyElementsOf(rows);
        verify(repository).findAllActiveYearSections(7L);
        verify(repository, never()).findAssignedActiveYearSections(anyLong(), anyLong());
    }

    @Test
    void teacherReceivesOnlyAssignedActiveYearSectionsWithoutAddingDuplicates() {
        authenticate("ROLE_TEACHER");
        List<AccessibleStudentAttendanceSectionResponse> rows = List.of(
                row(3L, 4L, "CLASS_1", 5L, "A")
        );
        when(repository.findAssignedActiveYearSections(7L, 11L))
                .thenReturn(rows);

        List<AccessibleStudentAttendanceSectionResponse> result =
                service.listAccessibleSections(7L, 11L);

        assertThat(result).containsExactlyElementsOf(rows);
        verify(repository).findAssignedActiveYearSections(7L, 11L);
        verify(repository, never()).findAllActiveYearSections(anyLong());
    }

    @Test
    void unassignedTeacherSeesEmptyRepositoryResultAndOtherRolesAreDenied() {
        authenticate("ROLE_TEACHER");
        when(repository.findAssignedActiveYearSections(7L, 11L))
                .thenReturn(List.of());

        assertThat(service.listAccessibleSections(7L, 11L)).isEmpty();

        authenticate("ROLE_STUDENT");
        assertThatThrownBy(() -> service.listAccessibleSections(7L, 11L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void invalidTenantOrActorIsRejectedBeforeRepositoryAccess() {
        authenticate("ROLE_TEACHER");

        assertThatThrownBy(() -> service.listAccessibleSections(0L, 11L))
                .isInstanceOf(InvalidStudentAttendanceRecordingException.class);
        assertThatThrownBy(() -> service.listAccessibleSections(7L, 0L))
                .isInstanceOf(InvalidStudentAttendanceRecordingException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void noActiveAcademicYearProducesEmptyList() {
        authenticate("ROLE_ADMIN");
        when(repository.findAllActiveYearSections(7L)).thenReturn(List.of());

        assertThat(service.listAccessibleSections(7L, 11L)).isEmpty();
    }

    private static AccessibleStudentAttendanceSectionResponse row(
            long academicYearId,
            long gradeLevelId,
            String gradeLevelCode,
            long sectionId,
            String sectionCode
    ) {
        return new AccessibleStudentAttendanceSectionResponse(
                academicYearId,
                "2026-2027",
                gradeLevelId,
                gradeLevelCode,
                gradeLevelCode.replace('_', ' '),
                sectionId,
                sectionCode,
                sectionCode
        );
    }

    private static void authenticate(String authority) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(
                        "actor",
                        "n/a",
                        List.of(new SimpleGrantedAuthority(authority))
                ));
    }
}
