package com.dawnrise.identity.permission.migration;

import com.dawnrise.identity.permission.enums.PermissionCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SeededSchoolPermissionCodeTest {
    private static final Pattern INSERT = Pattern.compile(
            "INSERT\\s+INTO\\s+permissions\\s*\\([^)]*\\)\\s*VALUES\\s*(.*?);",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CODE = Pattern.compile("\\(\\s*'([A-Z][A-Z0-9_]*)'\\s*,");

    @Test
    void everySeededSchoolUserPermissionCanBeReadThroughTheEntityEnum() throws IOException {
        List<String> unknown = new ArrayList<>();
        int checked = 0;
        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path file : files.filter(path -> path.getFileName().toString().matches("V[0-9]+__.*\\.sql")).toList()) {
                var inserts = INSERT.matcher(Files.readString(file));
                while (inserts.find()) {
                    var codes = CODE.matcher(inserts.group(1));
                    while (codes.find()) {
                        checked++;
                        try {
                            PermissionCode.valueOf(codes.group(1));
                        } catch (IllegalArgumentException exception) {
                            unknown.add(file.getFileName() + ": " + codes.group(1));
                        }
                    }
                }
            }
        }
        assertFalse(checked == 0, "The audit must find seeded school-user permission codes");
        assertEquals(List.of(), unknown, "Seeded permissions must resolve when a role is loaded");
    }
}
