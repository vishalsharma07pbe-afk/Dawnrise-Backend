package com.dawnrise.academic.academicyearrollover.service;

import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevelsubject.entity.GradeLevelSubject;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.subject.entity.Subject;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
public class RolloverFingerprintService {

    private static final int HASH_SCHEMA_VERSION = 1;

    public String requestHash(long organizationId, RolloverOptions options) {
        CanonicalWriter writer = new CanonicalWriter();
        writer.beginObject();
        writer.name("gradeLevelIds").longArray(options.gradeLevelIds());
        writer.name("hashSchemaVersion").number(HASH_SCHEMA_VERSION);
        writer.name("includeGradeLevelSubjects")
                .bool(options.includeGradeLevelSubjects());
        writer.name("includeGradeLevels").bool(options.includeGradeLevels());
        writer.name("includeSections").bool(options.includeSections());
        writer.name("includeSubjects").bool(options.includeSubjects());
        writer.name("organizationId").number(organizationId);
        writer.name("sourceAcademicYearId")
                .number(options.sourceAcademicYearId());
        writer.name("subjectIds").longArray(options.subjectIds());
        writer.name("targetAcademicYearId")
                .number(options.targetAcademicYearId());
        writer.endObject();
        return sha256(writer.toString());
    }

    public String previewFingerprint(RolloverPlan plan) {
        CanonicalWriter writer = new CanonicalWriter();
        writer.beginObject();
        writer.name("counts").beginObject();
        writer.name("gradeLevelSubjects")
                .number(plan.sourceGradeLevelSubjects().size());
        writer.name("gradeLevels").number(plan.sourceGradeLevels().size());
        writer.name("sections").number(plan.sourceSections().size());
        writer.name("subjects").number(plan.sourceSubjects().size());
        writer.name("totalGeneratedRecords")
                .number(plan.counts().totalGeneratedRecords());
        writer.endObject();
        writer.name("hashSchemaVersion").number(HASH_SCHEMA_VERSION);
        writer.name("requestHash").string(plan.requestHash());
        writer.name("sourceAcademicYear").beginObject();
        writer.name("id").number(plan.sourceAcademicYear().getId());
        writer.name("status").string(plan.sourceAcademicYear().getStatus().name());
        writer.name("version").number(plan.sourceAcademicYear().getVersion());
        writer.endObject();
        writer.name("sourceGradeLevelSubjects");
        gradeLevelSubjects(writer, plan.sourceGradeLevelSubjects());
        writer.name("sourceGradeLevels");
        gradeLevels(writer, plan.sourceGradeLevels());
        writer.name("sourceSections");
        sections(writer, plan.sourceSections());
        writer.name("sourceSubjects");
        subjects(writer, plan.sourceSubjects());
        writer.name("targetAcademicYear").beginObject();
        writer.name("id").number(plan.targetAcademicYear().getId());
        writer.name("status").string(plan.targetAcademicYear().getStatus().name());
        writer.name("version").number(plan.targetAcademicYear().getVersion());
        writer.endObject();
        writer.name("targetGradeLevelSubjects");
        gradeLevelSubjects(writer, plan.targetGradeLevelSubjects());
        writer.name("targetGradeLevels");
        gradeLevels(writer, plan.targetGradeLevels());
        writer.name("targetSections");
        sections(writer, plan.targetSections());
        writer.name("targetSubjects");
        subjects(writer, plan.targetSubjects());
        writer.endObject();
        return sha256(writer.toString());
    }

    private void gradeLevels(CanonicalWriter writer, List<GradeLevel> grades) {
        writer.beginArray();
        for (GradeLevel grade : grades) {
            writer.beginObject();
            writer.name("code").string(grade.getCode());
            writer.name("displayOrder").number(grade.getDisplayOrder());
            writer.name("id").number(grade.getId());
            writer.name("name").string(grade.getName());
            writer.name("version").number(grade.getVersion());
            writer.endObject();
        }
        writer.endArray();
    }

    private void sections(CanonicalWriter writer, List<Section> sections) {
        writer.beginArray();
        for (Section section : sections) {
            writer.beginObject();
            writer.name("code").string(section.getCode());
            writer.name("displayOrder").number(section.getDisplayOrder());
            writer.name("gradeLevelId").number(section.getGradeLevelId());
            writer.name("id").number(section.getId());
            writer.name("name").string(section.getName());
            writer.name("version").number(section.getVersion());
            writer.endObject();
        }
        writer.endArray();
    }

    private void subjects(CanonicalWriter writer, List<Subject> subjects) {
        writer.beginArray();
        for (Subject subject : subjects) {
            writer.beginObject();
            writer.name("code").string(subject.getCode());
            writer.name("description").string(subject.getDescription());
            writer.name("id").number(subject.getId());
            writer.name("name").string(subject.getName());
            writer.name("version").number(subject.getVersion());
            writer.endObject();
        }
        writer.endArray();
    }

    private void gradeLevelSubjects(
            CanonicalWriter writer,
            List<GradeLevelSubject> assignments
    ) {
        writer.beginArray();
        for (GradeLevelSubject assignment : assignments) {
            writer.beginObject();
            writer.name("displayOrder").number(assignment.getDisplayOrder());
            writer.name("gradeLevelId").number(assignment.getGradeLevelId());
            writer.name("id").number(assignment.getId());
            writer.name("mandatory").bool(assignment.getMandatory());
            writer.name("subjectId").number(assignment.getSubjectId());
            writer.name("version").number(assignment.getVersion());
            writer.endObject();
        }
        writer.endArray();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(
                    digest.digest(input.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static class CanonicalWriter {
        private final StringBuilder builder = new StringBuilder();
        private final java.util.Deque<Boolean> firstStack =
                new java.util.ArrayDeque<>();
        private boolean expectingValue;

        CanonicalWriter name(String name) {
            commaIfNeeded();
            rawString(name);
            builder.append(':');
            expectingValue = true;
            return this;
        }

        CanonicalWriter beginObject() {
            commaIfNeeded();
            builder.append('{');
            firstStack.push(true);
            expectingValue = false;
            return this;
        }

        CanonicalWriter endObject() {
            builder.append('}');
            firstStack.pop();
            return this;
        }

        CanonicalWriter beginArray() {
            commaIfNeeded();
            builder.append('[');
            firstStack.push(true);
            expectingValue = false;
            return this;
        }

        CanonicalWriter endArray() {
            builder.append(']');
            firstStack.pop();
            return this;
        }

        CanonicalWriter string(String value) {
            commaIfNeeded();
            if (value == null) {
                builder.append("null");
                expectingValue = false;
                return this;
            }
            rawString(value);
            expectingValue = false;
            return this;
        }

        private void rawString(String value) {
            builder.append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> builder.append("\\\"");
                    case '\\' -> builder.append("\\\\");
                    case '\b' -> builder.append("\\b");
                    case '\f' -> builder.append("\\f");
                    case '\n' -> builder.append("\\n");
                    case '\r' -> builder.append("\\r");
                    case '\t' -> builder.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            builder.append(String.format("\\u%04x", (int) c));
                        } else {
                            builder.append(c);
                        }
                    }
                }
            }
            builder.append('"');
        }

        CanonicalWriter number(Number value) {
            commaIfNeeded();
            builder.append(value == null ? "null" : value);
            expectingValue = false;
            return this;
        }

        CanonicalWriter bool(Boolean value) {
            commaIfNeeded();
            builder.append(value == null ? "null" : value);
            expectingValue = false;
            return this;
        }

        CanonicalWriter longArray(List<Long> values) {
            beginArray();
            for (Long value : values) {
                number(value);
            }
            endArray();
            return this;
        }

        private void commaIfNeeded() {
            if (expectingValue) {
                return;
            }
            if (firstStack.isEmpty()) {
                return;
            }
            if (firstStack.pop()) {
                firstStack.push(false);
            } else {
                firstStack.push(false);
                builder.append(',');
            }
        }

        @Override
        public String toString() {
            return builder.toString();
        }
    }
}
