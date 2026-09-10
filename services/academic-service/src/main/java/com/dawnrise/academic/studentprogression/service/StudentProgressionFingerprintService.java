package com.dawnrise.academic.studentprogression.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionConflictResponse;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionDecisionRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Component
public class StudentProgressionFingerprintService {

    private static final int HASH_SCHEMA_VERSION = 1;

    public String requestHash(
            long organizationId,
            long sourceAcademicYearId,
            long targetAcademicYearId,
            String batchLabel,
            List<StudentProgressionDecisionRequest> decisions
    ) {
        CanonicalWriter writer = new CanonicalWriter();

        List<StudentProgressionDecisionRequest> orderedDecisions =
                decisions.stream()
                        .sorted(Comparator.comparing(
                                StudentProgressionDecisionRequest
                                        ::sourceEnrollmentId
                        ))
                        .toList();

        writer.beginObject();
        writer.name("batchLabel").string(normalize(batchLabel));
        writer.name("decisions");
        decisions(writer, orderedDecisions);
        writer.name("hashSchemaVersion").number(HASH_SCHEMA_VERSION);
        writer.name("organizationId").number(organizationId);
        writer.name("sourceAcademicYearId")
                .number(sourceAcademicYearId);
        writer.name("targetAcademicYearId")
                .number(targetAcademicYearId);
        writer.endObject();

        return sha256(writer.toString());
    }

    public String previewFingerprint(
            String requestHash,
            StudentProgressionPlan plan
    ) {
        List<StudentProgressionPlanItem> orderedItems =
                plan.items().stream()
                        .sorted(Comparator.comparing(
                                item -> item.decision()
                                        .sourceEnrollmentId()
                        ))
                        .toList();

        CanonicalWriter writer = new CanonicalWriter();

        writer.beginObject();
        writer.name("conflicts");
        conflicts(writer, plan.conflicts());
        writer.name("hashSchemaVersion").number(HASH_SCHEMA_VERSION);
        writer.name("items");
        planItems(writer, orderedItems);
        writer.name("requestHash").string(requestHash);
        writer.name("sourceAcademicYear");
        academicYear(writer, plan.sourceAcademicYear());
        writer.name("targetAcademicYear");
        academicYear(writer, plan.targetAcademicYear());
        writer.endObject();

        return sha256(writer.toString());
    }

    private void decisions(
            CanonicalWriter writer,
            List<StudentProgressionDecisionRequest> decisions
    ) {
        writer.beginArray();

        for (StudentProgressionDecisionRequest decision : decisions) {
            writer.beginObject();
            writer.name("effectiveOn").string(
                    decision.effectiveOn() == null
                            ? null
                            : decision.effectiveOn().toString()
            );
            writer.name("note").string(normalize(decision.note()));
            writer.name("outcome").string(
                    decision.outcome() == null
                            ? null
                            : decision.outcome().name()
            );
            writer.name("sourceEnrollmentId")
                    .number(decision.sourceEnrollmentId());
            writer.name("targetGradeLevelId")
                    .number(decision.targetGradeLevelId());
            writer.name("targetRollNumber")
                    .string(normalizeRollNumber(
                            decision.targetRollNumber()
                    ));
            writer.name("targetSectionId")
                    .number(decision.targetSectionId());
            writer.endObject();
        }

        writer.endArray();
    }

    private void planItems(
            CanonicalWriter writer,
            List<StudentProgressionPlanItem> items
    ) {
        writer.beginArray();

        for (StudentProgressionPlanItem item : items) {
            writer.beginObject();

            writer.name("conflicts");
            conflicts(writer, item.conflicts());

            writer.name("decision");
            decisions(writer, List.of(item.decision()));

            writer.name("sourceEnrollment");
            enrollment(writer, item.sourceEnrollment());

            writer.name("sourceGradeLevel");
            gradeLevel(writer, item.sourceGradeLevel());

            writer.name("sourceSection");
            section(writer, item.sourceSection());

            writer.name("suggestedTargetGradeLevelId")
                    .number(item.suggestedTargetGradeLevelId());

            writer.name("suggestedTargetSectionId")
                    .number(item.suggestedTargetSectionId());

            writer.name("targetGradeLevel");
            gradeLevel(writer, item.targetGradeLevel());

            writer.name("targetSection");
            section(writer, item.targetSection());

            writer.endObject();
        }

        writer.endArray();
    }

    private void academicYear(
            CanonicalWriter writer,
            AcademicYear academicYear
    ) {
        if (academicYear == null) {
            writer.nullValue();
            return;
        }

        writer.beginObject();
        writer.name("endDate")
                .string(academicYear.getEndDate().toString());
        writer.name("id").number(academicYear.getId());
        writer.name("startDate")
                .string(academicYear.getStartDate().toString());
        writer.name("status")
                .string(academicYear.getStatus().name());
        writer.name("version").number(academicYear.getVersion());
        writer.endObject();
    }

    private void enrollment(
            CanonicalWriter writer,
            StudentEnrollment enrollment
    ) {
        if (enrollment == null) {
            writer.nullValue();
            return;
        }

        writer.beginObject();
        writer.name("academicYearId")
                .number(enrollment.getAcademicYearId());
        writer.name("endedOn").string(
                enrollment.getEndedOn() == null
                        ? null
                        : enrollment.getEndedOn().toString()
        );
        writer.name("enrolledOn")
                .string(enrollment.getEnrolledOn().toString());
        writer.name("gradeLevelId")
                .number(enrollment.getGradeLevelId());
        writer.name("id").number(enrollment.getId());
        writer.name("rollNumber")
                .string(enrollment.getRollNumber());
        writer.name("sectionId")
                .number(enrollment.getSectionId());
        writer.name("status")
                .string(enrollment.getStatus().name());
        writer.name("studentUserId")
                .number(enrollment.getStudentUserId());
        writer.name("version").number(enrollment.getVersion());
        writer.endObject();
    }

    private void gradeLevel(
            CanonicalWriter writer,
            GradeLevel gradeLevel
    ) {
        if (gradeLevel == null) {
            writer.nullValue();
            return;
        }

        writer.beginObject();
        writer.name("code").string(gradeLevel.getCode());
        writer.name("displayOrder")
                .number(gradeLevel.getDisplayOrder());
        writer.name("id").number(gradeLevel.getId());
        writer.name("name").string(gradeLevel.getName());
        writer.name("version").number(gradeLevel.getVersion());
        writer.endObject();
    }

    private void section(
            CanonicalWriter writer,
            Section section
    ) {
        if (section == null) {
            writer.nullValue();
            return;
        }

        writer.beginObject();
        writer.name("code").string(section.getCode());
        writer.name("displayOrder")
                .number(section.getDisplayOrder());
        writer.name("gradeLevelId")
                .number(section.getGradeLevelId());
        writer.name("id").number(section.getId());
        writer.name("name").string(section.getName());
        writer.name("version").number(section.getVersion());
        writer.endObject();
    }

    private void conflicts(
            CanonicalWriter writer,
            List<StudentProgressionConflictResponse> conflicts
    ) {
        List<StudentProgressionConflictResponse> orderedConflicts =
                conflicts.stream()
                        .sorted(Comparator
                                .comparing(
                                        StudentProgressionConflictResponse::code,
                                        Comparator.nullsFirst(
                                                Comparator.naturalOrder()
                                        )
                                )
                                .thenComparing(
                                        StudentProgressionConflictResponse
                                                ::sourceEnrollmentId,
                                        Comparator.nullsFirst(
                                                Comparator.naturalOrder()
                                        )
                                )
                                .thenComparing(
                                        StudentProgressionConflictResponse::field,
                                        Comparator.nullsFirst(
                                                Comparator.naturalOrder()
                                        )
                                ))
                        .toList();

        writer.beginArray();

        for (StudentProgressionConflictResponse conflict
                : orderedConflicts) {
            writer.beginObject();
            writer.name("code").string(conflict.code());
            writer.name("field").string(conflict.field());
            writer.name("message").string(conflict.message());
            writer.name("sourceEnrollmentId")
                    .number(conflict.sourceEnrollmentId());
            writer.endObject();
        }

        writer.endArray();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String normalizeRollNumber(String value) {
        String normalized = normalize(value);

        return normalized == null
                ? null
                : normalized.toUpperCase(java.util.Locale.ROOT);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            return "sha256:" + HexFormat.of().formatHex(
                    digest.digest(
                            input.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    private static class CanonicalWriter {

        private final StringBuilder builder =
                new StringBuilder();

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
            expectingValue = false;
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
            expectingValue = false;
            return this;
        }

        CanonicalWriter string(String value) {
            commaIfNeeded();

            if (value == null) {
                builder.append("null");
            } else {
                rawString(value);
            }

            expectingValue = false;
            return this;
        }

        CanonicalWriter number(Number value) {
            commaIfNeeded();
            builder.append(value == null ? "null" : value);
            expectingValue = false;
            return this;
        }

        CanonicalWriter nullValue() {
            commaIfNeeded();
            builder.append("null");
            expectingValue = false;
            return this;
        }

        private void rawString(String value) {
            builder.append('"');

            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);

                switch (character) {
                    case '"' -> builder.append("\\\"");
                    case '\\' -> builder.append("\\\\");
                    case '\b' -> builder.append("\\b");
                    case '\f' -> builder.append("\\f");
                    case '\n' -> builder.append("\\n");
                    case '\r' -> builder.append("\\r");
                    case '\t' -> builder.append("\\t");
                    default -> {
                        if (character < 0x20) {
                            builder.append(
                                    String.format(
                                            "\\u%04x",
                                            (int) character
                                    )
                            );
                        } else {
                            builder.append(character);
                        }
                    }
                }
            }

            builder.append('"');
        }

        private void commaIfNeeded() {
            if (expectingValue || firstStack.isEmpty()) {
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