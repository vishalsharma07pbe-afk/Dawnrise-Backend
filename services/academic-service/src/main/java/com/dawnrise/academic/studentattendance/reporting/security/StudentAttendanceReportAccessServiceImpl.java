package com.dawnrise.academic.studentattendance.reporting.security;

import com.dawnrise.academic.studentattendance.reporting.exception.InvalidStudentAttendanceReportException;
import com.dawnrise.academic.studentattendance.security.StudentAttendanceAccessRoles;
import com.dawnrise.academic.teacherassignment.repository.TeacherAssignmentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class StudentAttendanceReportAccessServiceImpl
        implements StudentAttendanceReportAccessService {

    private final TeacherAssignmentRepository
            teacherAssignmentRepository;

    public StudentAttendanceReportAccessServiceImpl(
            TeacherAssignmentRepository
                    teacherAssignmentRepository
    ) {
        this.teacherAssignmentRepository =
                teacherAssignmentRepository;
    }

    @Override
    public void requireLeadershipAccess() {
        if (!hasLeadershipAccess()) {
            throw new AccessDeniedException("Access Denied");
        }
    }

    @Override
    public void requireSectionAccess(
            long organizationId,
            long academicYearId,
            long gradeLevelId,
            long sectionId,
            long actorUserId
    ) {
        validatePositive(
                organizationId,
                "Organization ID must be positive"
        );

        validatePositive(
                academicYearId,
                "Academic year ID must be positive"
        );

        validatePositive(
                gradeLevelId,
                "Grade level ID must be positive"
        );

        validatePositive(
                sectionId,
                "Section ID must be positive"
        );

        validatePositive(
                actorUserId,
                "Actor user ID must be positive"
        );

        if (hasLeadershipAccess()) {
            return;
        }

        Set<String> authorities = currentAuthorities();

        boolean assignedTeacher =
                StudentAttendanceAccessRoles.hasTeacherAccess(authorities)
                        && teacherAssignmentRepository
                        .existsByOrganizationIdAndAcademicYearIdAndGradeLevelIdAndSectionIdAndTeacherUserId(
                                organizationId,
                                academicYearId,
                                gradeLevelId,
                                sectionId,
                                actorUserId
                        );

        if (!assignedTeacher) {
            throw new AccessDeniedException("Access Denied");
        }
    }

    @Override
    public boolean hasLeadershipAccess() {
        Set<String> authorities = currentAuthorities();

        return StudentAttendanceAccessRoles.hasLeadershipAccess(authorities);
    }

    private Set<String> currentAuthorities() {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {
            return Set.of();
        }

        return StudentAttendanceAccessRoles.authorityNames(
                authentication.getAuthorities()
        );
    }

    private void validatePositive(
            long value,
            String message
    ) {
        if (value <= 0) {
            throw new InvalidStudentAttendanceReportException(
                    message
            );
        }
    }
}
