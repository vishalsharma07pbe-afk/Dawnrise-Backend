package com.dawnrise.academic.studentattendance.reporting.security;

import com.dawnrise.academic.studentattendance.reporting.exception.InvalidStudentAttendanceReportException;
import com.dawnrise.academic.teacherassignment.repository.TeacherAssignmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StudentAttendanceReportAccessServiceImplTest {

    private TeacherAssignmentRepository teacherAssignmentRepository;
    private StudentAttendanceReportAccessServiceImpl accessService;

    @BeforeEach
    void setUp() {
        teacherAssignmentRepository =
                mock(TeacherAssignmentRepository.class);
        accessService = new StudentAttendanceReportAccessServiceImpl(
                teacherAssignmentRepository
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void leadershipRolesHaveLeadershipAndSectionAccess() {
        for (String role : List.of(
                "ROLE_ADMIN",
                "ROLE_PRINCIPAL",
                "ROLE_VICE_PRINCIPAL"
        )) {
            authenticate(role);

            assertThatCode(() -> accessService.requireLeadershipAccess())
                    .doesNotThrowAnyException();
            assertThatCode(() -> accessService.requireSectionAccess(
                    7L,
                    8L,
                    9L,
                    10L,
                    11L
            )).doesNotThrowAnyException();
        }

        verifyNoInteractions(teacherAssignmentRepository);
    }

    @Test
    void assignedTeacherCanAccessOwnSection() {
        authenticate("ROLE_TEACHER");
        when(teacherAssignmentRepository
                .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndTeacherUserId(
                        7L,
                        8L,
                        9L,
                        10L,
                        11L
                )).thenReturn(true);

        assertThatCode(() -> accessService.requireSectionAccess(
                7L,
                8L,
                9L,
                10L,
                11L
        )).doesNotThrowAnyException();
    }

    @Test
    void unassignedTeacherAndNonTeacherAreDeniedSectionAccess() {
        authenticate("ROLE_TEACHER");

        assertThatThrownBy(() -> accessService.requireSectionAccess(
                7L,
                8L,
                9L,
                10L,
                11L
        )).isInstanceOf(AccessDeniedException.class);

        authenticate("ROLE_STUDENT");

        assertThatThrownBy(() -> accessService.requireSectionAccess(
                7L,
                8L,
                9L,
                10L,
                11L
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void teacherCannotAccessLeadershipReports() {
        authenticate("ROLE_TEACHER");

        assertThatThrownBy(() -> accessService.requireLeadershipAccess())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingOrUnauthenticatedContextIsDenied() {
        assertThatThrownBy(() -> accessService.requireLeadershipAccess())
                .isInstanceOf(AccessDeniedException.class);

        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(
                        "actor",
                        "n/a",
                        "ROLE_ADMIN"
                );
        authentication.setAuthenticated(false);
        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        assertThatThrownBy(() -> accessService.requireSectionAccess(
                7L,
                8L,
                9L,
                10L,
                11L
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void invalidActorAndHierarchyIdsFailBeforeAuthorizationLookup() {
        authenticate("ROLE_TEACHER");

        assertThatThrownBy(() -> accessService.requireSectionAccess(
                0L,
                8L,
                9L,
                10L,
                11L
        )).isInstanceOf(InvalidStudentAttendanceReportException.class);
        assertThatThrownBy(() -> accessService.requireSectionAccess(
                7L,
                8L,
                9L,
                10L,
                0L
        )).isInstanceOf(InvalidStudentAttendanceReportException.class);

        verifyNoInteractions(teacherAssignmentRepository);
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
