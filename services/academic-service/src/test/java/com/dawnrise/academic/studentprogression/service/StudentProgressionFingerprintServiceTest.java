package com.dawnrise.academic.studentprogression.service;

import com.dawnrise.academic.academicyear.entity.AcademicYear;
import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.section.entity.Section;
import com.dawnrise.academic.studentenrollment.entity.StudentEnrollment;
import com.dawnrise.academic.studentprogression.dto.StudentProgressionDecisionRequest;
import com.dawnrise.academic.studentprogression.enums.StudentProgressionOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudentProgressionFingerprintServiceTest {

    private StudentProgressionFingerprintService service;

    @BeforeEach
    void setUp() {
        service = new StudentProgressionFingerprintService();
    }

    @Test
    void sameSemanticInputGivesSameRequestHash() {
        String first = service.requestHash(
                10L,
                1L,
                2L,
                " Annual promotion ",
                List.of(decision(100L))
        );
        String second = service.requestHash(
                10L,
                1L,
                2L,
                "Annual promotion",
                List.of(decision(100L))
        );

        assertThat(first).isEqualTo(second);
        assertThat(first).matches("sha256:[0-9a-f]{64}");
    }

    @Test
    void decisionOrderDoesNotChangeRequestHash() {
        String first = service.requestHash(
                10L,
                1L,
                2L,
                "Annual promotion",
                List.of(decision(101L), decision(100L))
        );
        String second = service.requestHash(
                10L,
                1L,
                2L,
                "Annual promotion",
                List.of(decision(100L), decision(101L))
        );

        assertThat(first).isEqualTo(second);
    }

    @Test
    void requestHashChangesWhenDecisionFieldsChange() {
        String base = requestHash(decision(100L));

        assertThat(requestHash(new StudentProgressionDecisionRequest(
                100L,
                StudentProgressionOutcome.REPEATED,
                201L,
                301L,
                "A-1",
                effectiveOn(),
                "note"
        ))).isNotEqualTo(base);
        assertThat(requestHash(new StudentProgressionDecisionRequest(
                100L,
                StudentProgressionOutcome.PROMOTED,
                202L,
                301L,
                "A-1",
                effectiveOn(),
                "note"
        ))).isNotEqualTo(base);
        assertThat(requestHash(new StudentProgressionDecisionRequest(
                100L,
                StudentProgressionOutcome.PROMOTED,
                201L,
                302L,
                "A-1",
                effectiveOn(),
                "note"
        ))).isNotEqualTo(base);
        assertThat(requestHash(new StudentProgressionDecisionRequest(
                100L,
                StudentProgressionOutcome.PROMOTED,
                201L,
                301L,
                "A-2",
                effectiveOn(),
                "note"
        ))).isNotEqualTo(base);
        assertThat(requestHash(new StudentProgressionDecisionRequest(
                100L,
                StudentProgressionOutcome.PROMOTED,
                201L,
                301L,
                "A-1",
                LocalDate.of(2025, 12, 30),
                "note"
        ))).isNotEqualTo(base);
        assertThat(requestHash(new StudentProgressionDecisionRequest(
                100L,
                StudentProgressionOutcome.PROMOTED,
                201L,
                301L,
                "A-1",
                effectiveOn(),
                "updated note"
        ))).isNotEqualTo(base);
    }

    @Test
    void rollNumberCaseAndWhitespaceNormalizeConsistently() {
        String first = requestHash(new StudentProgressionDecisionRequest(
                100L,
                StudentProgressionOutcome.PROMOTED,
                201L,
                301L,
                " a-1 ",
                effectiveOn(),
                "note"
        ));
        String second = requestHash(new StudentProgressionDecisionRequest(
                100L,
                StudentProgressionOutcome.PROMOTED,
                201L,
                301L,
                "A-1",
                effectiveOn(),
                "note"
        ));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void academicYearStateAndVersionAffectPreviewFingerprint() {
        String requestHash = requestHash(decision(100L));
        StudentProgressionPlan plan = plan(sourceYear(AcademicYearStatus.CLOSED, 0L));
        StudentProgressionPlan changed = new StudentProgressionPlan(
                sourceYear(AcademicYearStatus.ACTIVE, 1L),
                plan.targetAcademicYear(),
                plan.batchLabel(),
                plan.items(),
                plan.conflicts(),
                plan.previewFingerprint()
        );

        assertThat(service.previewFingerprint(requestHash, changed))
                .isNotEqualTo(service.previewFingerprint(requestHash, plan));
    }

    @Test
    void enrollmentGradeAndSectionStateChangesAffectPreviewFingerprint() {
        String requestHash = requestHash(decision(100L));
        StudentProgressionPlan base = plan(sourceYear(AcademicYearStatus.CLOSED, 0L));

        assertThat(service.previewFingerprint(
                requestHash,
                planWithItem(item(enrollment(100L, "S-2", 1L), sourceGrade(), sourceSection()))
        )).isNotEqualTo(service.previewFingerprint(requestHash, base));

        assertThat(service.previewFingerprint(
                requestHash,
                planWithItem(item(sourceEnrollment(), grade(200L, "G1", 2L), sourceSection()))
        )).isNotEqualTo(service.previewFingerprint(requestHash, base));

        assertThat(service.previewFingerprint(
                requestHash,
                planWithItem(item(sourceEnrollment(), sourceGrade(), section(300L, "B", 1L)))
        )).isNotEqualTo(service.previewFingerprint(requestHash, base));
    }

    private String requestHash(StudentProgressionDecisionRequest decision) {
        return service.requestHash(
                10L,
                1L,
                2L,
                "Annual promotion",
                List.of(decision)
        );
    }

    private static StudentProgressionDecisionRequest decision(Long sourceId) {
        return new StudentProgressionDecisionRequest(
                sourceId,
                StudentProgressionOutcome.PROMOTED,
                201L,
                301L,
                "A-1",
                effectiveOn(),
                "note"
        );
    }

    private static StudentProgressionPlan plan(AcademicYear sourceYear) {
        return new StudentProgressionPlan(
                sourceYear,
                targetYear(),
                "Annual promotion",
                List.of(item(sourceEnrollment(), sourceGrade(), sourceSection())),
                List.of(),
                hash('b')
        );
    }

    private static StudentProgressionPlan planWithItem(
            StudentProgressionPlanItem item
    ) {
        return new StudentProgressionPlan(
                sourceYear(AcademicYearStatus.CLOSED, 0L),
                targetYear(),
                "Annual promotion",
                List.of(item),
                List.of(),
                hash('b')
        );
    }

    private static StudentProgressionPlanItem item(
            StudentEnrollment sourceEnrollment,
            GradeLevel sourceGrade,
            Section sourceSection
    ) {
        return new StudentProgressionPlanItem(
                decision(sourceEnrollment.getId()),
                sourceEnrollment,
                sourceGrade,
                sourceSection,
                targetGrade(),
                targetSection(),
                201L,
                301L,
                List.of()
        );
    }

    private static AcademicYear sourceYear(
            AcademicYearStatus status,
            Long version
    ) {
        AcademicYear year = new AcademicYear(
                10L,
                "Source",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31)
        );
        ReflectionTestUtils.setField(year, "id", 1L);
        ReflectionTestUtils.setField(year, "status", status);
        ReflectionTestUtils.setField(year, "version", version);
        return year;
    }

    private static AcademicYear targetYear() {
        AcademicYear year = new AcademicYear(
                10L,
                "Target",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );
        ReflectionTestUtils.setField(year, "id", 2L);
        ReflectionTestUtils.setField(year, "status", AcademicYearStatus.PLANNED);
        ReflectionTestUtils.setField(year, "version", 0L);
        return year;
    }

    private static StudentEnrollment sourceEnrollment() {
        return enrollment(100L, "S-1", 0L);
    }

    private static StudentEnrollment enrollment(
            Long id,
            String rollNumber,
            Long version
    ) {
        StudentEnrollment enrollment = new StudentEnrollment(
                10L,
                1L,
                200L,
                300L,
                1000L,
                rollNumber,
                LocalDate.of(2025, 1, 1)
        );
        ReflectionTestUtils.setField(enrollment, "id", id);
        ReflectionTestUtils.setField(enrollment, "version", version);
        return enrollment;
    }

    private static GradeLevel sourceGrade() {
        return grade(200L, "G1", 0L);
    }

    private static GradeLevel targetGrade() {
        return grade(201L, "G2", 0L);
    }

    private static GradeLevel grade(
            Long id,
            String code,
            Long version
    ) {
        GradeLevel grade = new GradeLevel(
                10L,
                1L,
                code,
                "Grade " + code,
                id.equals(200L) ? 1 : 2
        );
        ReflectionTestUtils.setField(grade, "id", id);
        ReflectionTestUtils.setField(grade, "version", version);
        return grade;
    }

    private static Section sourceSection() {
        return section(300L, "A", 0L);
    }

    private static Section targetSection() {
        return section(301L, "A", 0L);
    }

    private static Section section(Long id, String code, Long version) {
        Section section = new Section(
                10L,
                1L,
                id.equals(300L) ? 200L : 201L,
                code,
                "Section " + code,
                1
        );
        ReflectionTestUtils.setField(section, "id", id);
        ReflectionTestUtils.setField(section, "version", version);
        return section;
    }

    private static LocalDate effectiveOn() {
        return LocalDate.of(2025, 12, 31);
    }

    private static String hash(char value) {
        return "sha256:" + String.valueOf(value).repeat(64);
    }
}
