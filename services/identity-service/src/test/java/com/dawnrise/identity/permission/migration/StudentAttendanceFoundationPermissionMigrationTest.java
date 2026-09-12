package com.dawnrise.identity.permission.migration;

import com.dawnrise.identity.permission.enums.PermissionCode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendanceFoundationPermissionMigrationTest {

    private static final Path V44 = Path.of(
            "src/main/resources/db/migration/"
                    + "V44__add_student_attendance_foundation_permissions.sql"
    );

    private static final Path V43 = Path.of(
            "src/main/resources/db/migration/"
                    + "V43__create_student_guardian_relationships.sql"
    );

    private static final Set<String> PERMISSION_CODES = Set.of(
            "STUDENT_ATTENDANCE_POLICY_VIEW",
            "STUDENT_ATTENDANCE_POLICY_MANAGE",
            "STUDENT_ATTENDANCE_CALENDAR_VIEW",
            "STUDENT_ATTENDANCE_CALENDAR_MANAGE"
    );

    private static final Set<String> FORBIDDEN_ATTENDANCE_CODES = Set.of(
            "STUDENT_ATTENDANCE_TAKE",
            "STUDENT_ATTENDANCE_REPORT_VIEW",
            "STUDENT_ATTENDANCE_SELF_VIEW",
            "STUDENT_ATTENDANCE_LINKED_STUDENT_VIEW",
            "STUDENT_ATTENDANCE_DELEGATION_MANAGE",
            "STUDENT_ATTENDANCE_CORRECTION_MANAGE"
    );

    private static final Set<String> STAFF_VIEW_ROLES = Set.of(
            "ADMIN",
            "PRINCIPAL",
            "VICE_PRINCIPAL",
            "TEACHER"
    );

    private static final Set<String> LEADERSHIP_MANAGE_ROLES = Set.of(
            "ADMIN",
            "PRINCIPAL",
            "VICE_PRINCIPAL"
    );

    private static final Set<String> CALENDAR_VIEW_ROLES = Set.of(
            "ADMIN",
            "PRINCIPAL",
            "VICE_PRINCIPAL",
            "TEACHER",
            "STUDENT",
            "PARENT"
    );

    @Test
    void v44SeedsExactFourStudentAttendanceFoundationPermissions()
            throws Exception {
        String sql = Files.readString(V44);

        assertThat(permissionRows(sql).keySet())
                .containsExactlyInAnyOrderElementsOf(PERMISSION_CODES);
        assertThat(FORBIDDEN_ATTENDANCE_CODES)
                .noneMatch(sql::contains);
    }

    @Test
    void v44SeedsOwningServiceAndSensitiveFlags() throws Exception {
        String sql = Files.readString(V44);

        assertThat(permissionRows(sql))
                .containsEntry(
                        "STUDENT_ATTENDANCE_POLICY_VIEW",
                        new PermissionMetadata("academic-service", false)
                )
                .containsEntry(
                        "STUDENT_ATTENDANCE_POLICY_MANAGE",
                        new PermissionMetadata("academic-service", true)
                )
                .containsEntry(
                        "STUDENT_ATTENDANCE_CALENDAR_VIEW",
                        new PermissionMetadata("academic-service", false)
                )
                .containsEntry(
                        "STUDENT_ATTENDANCE_CALENDAR_MANAGE",
                        new PermissionMetadata("academic-service", true)
                );
    }

    @Test
    void v44SeedsExactRoleGrantsForEachPermission() throws Exception {
        String sql = Files.readString(V44);

        assertThat(roleGrants(sql))
                .containsOnlyKeys(PERMISSION_CODES)
                .containsEntry(
                        "STUDENT_ATTENDANCE_POLICY_VIEW",
                        STAFF_VIEW_ROLES
                )
                .containsEntry(
                        "STUDENT_ATTENDANCE_POLICY_MANAGE",
                        LEADERSHIP_MANAGE_ROLES
                )
                .containsEntry(
                        "STUDENT_ATTENDANCE_CALENDAR_VIEW",
                        CALENDAR_VIEW_ROLES
                )
                .containsEntry(
                        "STUDENT_ATTENDANCE_CALENDAR_MANAGE",
                        LEADERSHIP_MANAGE_ROLES
                );
    }

    @Test
    void v44DoesNotGrantStudentAttendancePermissionsToOtherRoles()
            throws Exception {
        String sql = Files.readString(V44);

        roleGrants(sql).forEach((permission, roles) -> {
            if (permission.endsWith("_MANAGE")) {
                assertThat(roles).isEqualTo(LEADERSHIP_MANAGE_ROLES);
            } else if (permission.equals("STUDENT_ATTENDANCE_POLICY_VIEW")) {
                assertThat(roles).isEqualTo(STAFF_VIEW_ROLES);
            } else {
                assertThat(roles).isEqualTo(CALENDAR_VIEW_ROLES);
            }
        });
    }

    @Test
    void permissionCodeEnumContainsAllFourCodes() {
        assertThat(PermissionCode.values())
                .contains(
                        PermissionCode.STUDENT_ATTENDANCE_POLICY_VIEW,
                        PermissionCode.STUDENT_ATTENDANCE_POLICY_MANAGE,
                        PermissionCode.STUDENT_ATTENDANCE_CALENDAR_VIEW,
                        PermissionCode.STUDENT_ATTENDANCE_CALENDAR_MANAGE
                );
    }

    @Test
    void v43IsUntouched() throws Exception {
        String hash = sha256(Files.readAllBytes(V43));

        assertThat(hash).isEqualTo(
                "4009513ea500f58c42b0273f9c377ec79b85ce104212bda42d54dcabfb17331d"
        );
    }

    @Test
    void v44UsesIdempotentPermissionAndRoleGrantInserts()
            throws Exception {
        String sql = Files.readString(V44);

        assertThat(sql)
                .contains("ON CONFLICT (code) DO NOTHING")
                .contains("ON CONFLICT (role, permission_id) DO NOTHING");
    }

    private static Map<String, PermissionMetadata> permissionRows(
            String sql
    ) {
        Pattern pattern = Pattern.compile(
                "\\(\\s*'([^']+)'\\s*,\\s*'[^']+'\\s*,\\s*"
                        + "'([^']+)'\\s*,\\s*(TRUE|FALSE)\\s*,\\s*TRUE\\s*\\)",
                Pattern.CASE_INSENSITIVE
        );

        return pattern.matcher(sql)
                .results()
                .filter(result -> PERMISSION_CODES.contains(result.group(1)))
                .collect(Collectors.toMap(
                        result -> result.group(1),
                        result -> new PermissionMetadata(
                                result.group(2),
                                Boolean.parseBoolean(
                                        result.group(3).toLowerCase()
                                )
                        )
                ));
    }

    private static Map<String, Set<String>> roleGrants(String sql) {
        Pattern blockPattern = Pattern.compile(
                "FROM \\(\\s*VALUES(?<roles>.*?)\\) AS roles\\(role\\)"
                        + ".*?WHERE permissions\\.code = "
                        + "'(?<permission>[^']+)'",
                Pattern.DOTALL
        );

        Pattern rolePattern = Pattern.compile("'([^']+)'");
        Matcher matcher = blockPattern.matcher(sql);

        return matcher.results()
                .filter(result -> PERMISSION_CODES.contains(
                        result.group("permission")
                ))
                .collect(Collectors.toMap(
                        result -> result.group("permission"),
                        result -> rolePattern.matcher(result.group("roles"))
                                .results()
                                .map(role -> role.group(1))
                                .collect(Collectors.toSet())
                ));
    }

    private static String sha256(byte[] content) throws Exception {
        byte[] digest = MessageDigest
                .getInstance("SHA-256")
                .digest(content);

        StringBuilder builder = new StringBuilder();
        for (byte value : digest) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private record PermissionMetadata(
            String owningService,
            boolean sensitive
    ) {
    }
}
