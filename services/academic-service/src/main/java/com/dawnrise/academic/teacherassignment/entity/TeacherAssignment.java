package com.dawnrise.academic.teacherassignment.entity;

import com.dawnrise.academic.teacherassignment.enums.TeacherAssignmentType;
import com.dawnrise.academic.teacherassignment.exception.InvalidTeacherAssignmentException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "teacher_assignments")
public class TeacherAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(name = "grade_level_id", nullable = false)
    private Long gradeLevelId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "grade_level_subject_id")
    private Long gradeLevelSubjectId;

    @Column(name = "teacher_user_id", nullable = false)
    private Long teacherUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false, length = 30)
    private TeacherAssignmentType assignmentType;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public TeacherAssignment() {
    }

    public TeacherAssignment(
            Long organizationId,
            Long academicYearId,
            Long gradeLevelId,
            Long sectionId,
            Long gradeLevelSubjectId,
            Long teacherUserId,
            TeacherAssignmentType assignmentType
    ) {
        if (organizationId == null || organizationId <= 0) {
            throw new InvalidTeacherAssignmentException(
                    "Organization ID must be greater than zero"
            );
        }

        if (academicYearId == null || academicYearId <= 0) {
            throw new InvalidTeacherAssignmentException(
                    "Academic year ID must be greater than zero"
            );
        }

        if (gradeLevelId == null || gradeLevelId <= 0) {
            throw new InvalidTeacherAssignmentException(
                    "Grade level ID must be greater than zero"
            );
        }

        if (sectionId == null || sectionId <= 0) {
            throw new InvalidTeacherAssignmentException(
                    "Section ID must be greater than zero"
            );
        }

        if (teacherUserId == null || teacherUserId <= 0) {
            throw new InvalidTeacherAssignmentException(
                    "Teacher user ID must be greater than zero"
            );
        }

        if (assignmentType == null) {
            throw new InvalidTeacherAssignmentException(
                    "Assignment type is required"
            );
        }

        validateSubjectRequirement(
                assignmentType,
                gradeLevelSubjectId
        );

        this.organizationId = organizationId;
        this.academicYearId = academicYearId;
        this.gradeLevelId = gradeLevelId;
        this.sectionId = sectionId;
        this.gradeLevelSubjectId = gradeLevelSubjectId;
        this.teacherUserId = teacherUserId;
        this.assignmentType = assignmentType;
    }

    public void replaceTeacher(Long teacherUserId) {
        if (teacherUserId == null || teacherUserId <= 0) {
            throw new InvalidTeacherAssignmentException(
                    "Teacher user ID must be greater than zero"
            );
        }

        this.teacherUserId = teacherUserId;
    }

    private void validateSubjectRequirement(
            TeacherAssignmentType assignmentType,
            Long gradeLevelSubjectId
    ) {
        if (assignmentType == TeacherAssignmentType.CLASS_TEACHER
                && gradeLevelSubjectId != null) {
            throw new InvalidTeacherAssignmentException(
                    "A class-teacher assignment cannot contain a subject"
            );
        }

        if (assignmentType == TeacherAssignmentType.SUBJECT_TEACHER
                && (gradeLevelSubjectId == null
                || gradeLevelSubjectId <= 0)) {
            throw new InvalidTeacherAssignmentException(
                    "A subject-teacher assignment requires a grade subject"
            );
        }
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getAcademicYearId() {
        return academicYearId;
    }

    public Long getGradeLevelId() {
        return gradeLevelId;
    }

    public Long getSectionId() {
        return sectionId;
    }

    public Long getGradeLevelSubjectId() {
        return gradeLevelSubjectId;
    }

    public Long getTeacherUserId() {
        return teacherUserId;
    }

    public TeacherAssignmentType getAssignmentType() {
        return assignmentType;
    }

    public Long getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}