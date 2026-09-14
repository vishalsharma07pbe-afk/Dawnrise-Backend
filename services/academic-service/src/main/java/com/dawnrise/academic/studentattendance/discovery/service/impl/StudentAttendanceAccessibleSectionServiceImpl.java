package com.dawnrise.academic.studentattendance.discovery.service.impl;

import com.dawnrise.academic.studentattendance.discovery.dto.AccessibleStudentAttendanceSectionResponse;
import com.dawnrise.academic.studentattendance.discovery.repository.StudentAttendanceAccessibleSectionQueryRepository;
import com.dawnrise.academic.studentattendance.discovery.service.StudentAttendanceAccessibleSectionService;
import com.dawnrise.academic.studentattendance.recording.exception.InvalidStudentAttendanceRecordingException;
import com.dawnrise.academic.studentattendance.security.StudentAttendanceAccessRoles;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class StudentAttendanceAccessibleSectionServiceImpl
        implements StudentAttendanceAccessibleSectionService {

    private final StudentAttendanceAccessibleSectionQueryRepository repository;

    public StudentAttendanceAccessibleSectionServiceImpl(
            StudentAttendanceAccessibleSectionQueryRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccessibleStudentAttendanceSectionResponse> listAccessibleSections(
            long organizationId,
            long actorUserId
    ) {
        validatePositive(organizationId, "Organization ID must be positive");
        validatePositive(actorUserId, "Actor user ID must be positive");

        Set<String> authorities = currentAuthorities();
        if (StudentAttendanceAccessRoles.hasLeadershipAccess(authorities)) {
            return List.copyOf(repository.findAllActiveYearSections(organizationId));
        }
        if (StudentAttendanceAccessRoles.hasTeacherAccess(authorities)) {
            return List.copyOf(repository.findAssignedActiveYearSections(
                    organizationId,
                    actorUserId
            ));
        }
        throw new AccessDeniedException("Access Denied");
    }

    private Set<String> currentAuthorities() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Set.of();
        }
        return StudentAttendanceAccessRoles.authorityNames(
                authentication.getAuthorities()
        );
    }

    private static void validatePositive(long value, String message) {
        if (value <= 0) {
            throw new InvalidStudentAttendanceRecordingException(message);
        }
    }
}
