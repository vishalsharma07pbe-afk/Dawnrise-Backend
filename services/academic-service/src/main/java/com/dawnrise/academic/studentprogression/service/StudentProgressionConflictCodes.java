package com.dawnrise.academic.studentprogression.service;

public final class StudentProgressionConflictCodes {

    public static final String SOURCE_YEAR_INVALID_STATUS =
            "SOURCE_YEAR_INVALID_STATUS";

    public static final String TARGET_YEAR_NOT_PLANNED =
            "TARGET_YEAR_NOT_PLANNED";

    public static final String TARGET_YEAR_NOT_AFTER_SOURCE =
            "TARGET_YEAR_NOT_AFTER_SOURCE";

    public static final String TARGET_STRUCTURE_NOT_READY =
            "TARGET_STRUCTURE_NOT_READY";

    public static final String DUPLICATE_SOURCE_DECISION =
            "DUPLICATE_SOURCE_DECISION";

    public static final String SOURCE_ENROLLMENT_NOT_FOUND =
            "SOURCE_ENROLLMENT_NOT_FOUND";

    public static final String SOURCE_ENROLLMENT_NOT_ACTIVE =
            "SOURCE_ENROLLMENT_NOT_ACTIVE";

    public static final String SOURCE_ENROLLMENT_ALREADY_PROGRESSED =
            "SOURCE_ENROLLMENT_ALREADY_PROGRESSED";

    public static final String EFFECTIVE_DATE_OUTSIDE_SOURCE_YEAR =
            "EFFECTIVE_DATE_OUTSIDE_SOURCE_YEAR";

    public static final String TARGET_FIELDS_REQUIRED =
            "TARGET_FIELDS_REQUIRED";

    public static final String TARGET_FIELDS_NOT_ALLOWED =
            "TARGET_FIELDS_NOT_ALLOWED";

    public static final String TARGET_GRADE_NOT_FOUND =
            "TARGET_GRADE_NOT_FOUND";

    public static final String TARGET_SECTION_NOT_FOUND =
            "TARGET_SECTION_NOT_FOUND";

    public static final String REPEATED_TARGET_NOT_EQUIVALENT =
            "REPEATED_TARGET_NOT_EQUIVALENT";

    public static final String PROMOTED_TARGET_NOT_HIGHER =
            "PROMOTED_TARGET_NOT_HIGHER";

    public static final String TARGET_STUDENT_ALREADY_ENROLLED =
            "TARGET_STUDENT_ALREADY_ENROLLED";

    public static final String TARGET_ROLL_NUMBER_CONFLICT =
            "TARGET_ROLL_NUMBER_CONFLICT";

    public static final String DUPLICATE_TARGET_ROLL_NUMBER =
            "DUPLICATE_TARGET_ROLL_NUMBER";

    public static final String STUDENT_NOT_ELIGIBLE =
            "STUDENT_NOT_ELIGIBLE";

    public static final String MANUAL_REVIEW_REQUIRED =
            "MANUAL_REVIEW_REQUIRED";

    private StudentProgressionConflictCodes() {
    }
}