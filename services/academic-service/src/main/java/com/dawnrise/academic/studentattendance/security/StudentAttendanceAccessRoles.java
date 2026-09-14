package com.dawnrise.academic.studentattendance.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public final class StudentAttendanceAccessRoles {

    public static final String TEACHER_AUTHORITY = "ROLE_TEACHER";

    private static final Set<String> LEADERSHIP_AUTHORITIES =
            Set.of(
                    "ROLE_ADMIN",
                    "ROLE_PRINCIPAL",
                    "ROLE_VICE_PRINCIPAL"
            );

    private StudentAttendanceAccessRoles() {
    }

    public static boolean hasLeadershipAccess(Set<String> authorities) {
        return LEADERSHIP_AUTHORITIES
                .stream()
                .anyMatch(authorities::contains);
    }

    public static boolean hasTeacherAccess(Set<String> authorities) {
        return authorities.contains(TEACHER_AUTHORITY);
    }

    public static Set<String> authorityNames(
            Collection<? extends GrantedAuthority> authorities
    ) {
        if (authorities == null) {
            return Set.of();
        }
        return authorities
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }
}
