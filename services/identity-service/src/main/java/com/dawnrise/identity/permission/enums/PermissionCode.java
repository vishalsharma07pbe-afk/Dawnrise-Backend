package com.dawnrise.identity.permission.enums;

public enum PermissionCode {

    /*
     * Own profile and account
     */
    PROFILE_VIEW_SELF,
    PROFILE_UPDATE_SELF,
    PASSWORD_CHANGE_SELF,
    LOGIN_HISTORY_VIEW_SELF,
    SESSION_VIEW_SELF,
    SESSION_REVOKE_SELF,

    /*
     * User account administration
     */
    USER_CREATE,
    USER_VIEW,
    USER_PROFILE_UPDATE,
    USER_ACTIVATE,
    USER_ACTIVATION_RESEND,
    USER_DEACTIVATE,
    USER_SUSPEND,
    USER_REACTIVATE,
    USER_UNLOCK,
    USER_PASSWORD_RESET_INITIATE,
    USER_LOGIN_HISTORY_VIEW,
    USER_SESSION_REVOKE,
    USER_EXPORT,

    /*
     * Role and permission visibility
     */
    ROLE_VIEW,
    PERMISSION_VIEW,
    ROLE_PERMISSION_VIEW,

    /*
     * Routine role management
     */
    ROLE_ASSIGN_ROUTINE,
    ROLE_REMOVE_ROUTINE,

    /*
     * Sensitive role-assignment workflow
     */
    ROLE_ASSIGNMENT_REQUEST_CREATE,
    ROLE_ASSIGNMENT_REQUEST_VIEW,
    ROLE_ASSIGNMENT_REQUEST_CANCEL,
    ROLE_ASSIGNMENT_APPROVE,

    /*
     * Sensitive role-removal workflow
     *
     * The role-removal workflow has not been implemented yet,
     * but these codes reserve the correct authorization model.
     */
    ROLE_REMOVAL_REQUEST_CREATE,
    ROLE_REMOVAL_REQUEST_VIEW,
    ROLE_REMOVAL_REQUEST_CANCEL,
    ROLE_REMOVAL_APPROVE,

    /*
     * Role-permission configuration
     *
     * Updating role-permission mappings is highly sensitive.
     * Initially, mappings will be seeded through Flyway rather
     * than exposed through a normal administration endpoint.
     */
    ROLE_PERMISSION_UPDATE,

    /*
     * Security monitoring
     */
    SECURITY_EVENT_VIEW,
    SECURITY_AUDIT_VIEW,
    SECURITY_AUDIT_EXPORT,

    /*
     * Academic-year management
     */
    ACADEMIC_YEAR_VIEW,
    ACADEMIC_YEAR_CREATE,
    ACADEMIC_YEAR_UPDATE,
    ACADEMIC_YEAR_ACTIVATE,
    ACADEMIC_YEAR_CLOSE,
    ACADEMIC_YEAR_VOID,
    ACADEMIC_YEAR_STRUCTURE_ROLLOVER,
    STUDENT_ANNUAL_PROGRESSION,

    /*
     * Security policy configuration
     */
    SECURITY_POLICY_VIEW,
    SECURITY_POLICY_UPDATE,

    /*
     * Grade-level management
     */
    GRADE_LEVEL_VIEW,
    GRADE_LEVEL_CREATE,
    GRADE_LEVEL_UPDATE,

    /*
     * Section management
     */
    SECTION_VIEW,
    SECTION_CREATE,
    SECTION_UPDATE,

    /*
     * Subject management
     */
    SUBJECT_VIEW,
    SUBJECT_CREATE,
    SUBJECT_UPDATE,

    /*
     * Grade-level subject assignments
     */
    GRADE_SUBJECT_VIEW,
    GRADE_SUBJECT_ASSIGN,
    GRADE_SUBJECT_UPDATE,
    GRADE_SUBJECT_REMOVE,

    /*
     * Teacher assignments
     */
    TEACHER_ASSIGNMENT_VIEW,
    TEACHER_ASSIGNMENT_CREATE,
    TEACHER_ASSIGNMENT_UPDATE,
    TEACHER_ASSIGNMENT_REMOVE,

    /*
     * Student enrollment
     */
    STUDENT_ENROLLMENT_VIEW,
    STUDENT_ENROLLMENT_CREATE,
    STUDENT_ENROLLMENT_UPDATE,
    STUDENT_ENROLLMENT_TRANSFER,
    STUDENT_ENROLLMENT_WITHDRAW,
    STUDENT_ENROLLMENT_COMPLETE,

    /*
     * Student-guardian relationships
     */
    STUDENT_GUARDIAN_RELATIONSHIP_VIEW,
    STUDENT_GUARDIAN_RELATIONSHIP_MANAGE,

    /*
     * Student attendance foundation
     */
    STUDENT_ATTENDANCE_POLICY_VIEW,
    STUDENT_ATTENDANCE_POLICY_MANAGE,
    STUDENT_ATTENDANCE_CALENDAR_VIEW,
    STUDENT_ATTENDANCE_CALENDAR_MANAGE,
    STUDENT_ATTENDANCE_VIEW,
    STUDENT_ATTENDANCE_RECORD,
    STUDENT_ATTENDANCE_SUBMIT,
    STUDENT_ATTENDANCE_CORRECTION_REQUEST,
    STUDENT_ATTENDANCE_CORRECTION_VIEW,
    STUDENT_ATTENDANCE_CORRECTION_APPROVE,
}
