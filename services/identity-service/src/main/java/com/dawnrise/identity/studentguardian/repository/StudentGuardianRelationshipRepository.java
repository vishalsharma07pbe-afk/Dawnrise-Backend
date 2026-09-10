package com.dawnrise.identity.studentguardian.repository;

import com.dawnrise.identity.studentguardian.entity.StudentGuardianRelationship;
import com.dawnrise.identity.studentguardian.enums.StudentGuardianRelationshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentGuardianRelationshipRepository
        extends JpaRepository<StudentGuardianRelationship, Long> {

    long countByOrganizationIdAndStudentUserIdAndStatus(
            Long organizationId,
            Long studentUserId,
            StudentGuardianRelationshipStatus status
    );

    boolean existsByOrganizationIdAndStudentUserIdAndGuardianUserIdAndStatus(
            Long organizationId,
            Long studentUserId,
            Long guardianUserId,
            StudentGuardianRelationshipStatus status
    );

    Optional<StudentGuardianRelationship>
    findByOrganizationIdAndStudentUserIdAndId(
            Long organizationId,
            Long studentUserId,
            Long id
    );

    List<StudentGuardianRelationship>
    findAllByOrganizationIdAndStudentUserIdAndStatusOrderByPrimaryGuardianDescStartedAtAscIdAsc(
            Long organizationId,
            Long studentUserId,
            StudentGuardianRelationshipStatus status
    );

    @Query("""
            select new com.dawnrise.identity.studentguardian.repository.StudentGuardianRelationshipView(
                relationship,
                student,
                guardian
            )
            from StudentGuardianRelationship relationship
            join User student
                on student.id = relationship.studentUserId
                and student.organizationId = relationship.organizationId
            join User guardian
                on guardian.id = relationship.guardianUserId
                and guardian.organizationId = relationship.organizationId
            where relationship.organizationId = :organizationId
                and relationship.studentUserId = :studentUserId
                and relationship.status = :status
            order by relationship.primaryGuardian desc,
                     relationship.startedAt asc,
                     relationship.id asc
            """)
    List<StudentGuardianRelationshipView> findActiveViewsByStudent(
            @Param("organizationId") Long organizationId,
            @Param("studentUserId") Long studentUserId,
            @Param("status") StudentGuardianRelationshipStatus status
    );

    @Query("""
            select new com.dawnrise.identity.studentguardian.repository.StudentGuardianRelationshipView(
                relationship,
                student,
                guardian
            )
            from StudentGuardianRelationship relationship
            join User student
                on student.id = relationship.studentUserId
                and student.organizationId = relationship.organizationId
            join User guardian
                on guardian.id = relationship.guardianUserId
                and guardian.organizationId = relationship.organizationId
            where relationship.organizationId = :organizationId
                and relationship.studentUserId = :studentUserId
            order by relationship.startedAt desc,
                     relationship.id desc
            """)
    List<StudentGuardianRelationshipView> findHistoryViewsByStudent(
            @Param("organizationId") Long organizationId,
            @Param("studentUserId") Long studentUserId
    );

    @Query("""
            select new com.dawnrise.identity.studentguardian.repository.StudentGuardianRelationshipView(
                relationship,
                student,
                guardian
            )
            from StudentGuardianRelationship relationship
            join User student
                on student.id = relationship.studentUserId
                and student.organizationId = relationship.organizationId
            join User guardian
                on guardian.id = relationship.guardianUserId
                and guardian.organizationId = relationship.organizationId
            where relationship.organizationId = :organizationId
                and relationship.guardianUserId = :guardianUserId
                and relationship.status = :status
            order by student.firstName asc,
                     student.lastName asc,
                     relationship.startedAt asc,
                     relationship.id asc
            """)
    List<StudentGuardianRelationshipView> findActiveViewsByGuardian(
            @Param("organizationId") Long organizationId,
            @Param("guardianUserId") Long guardianUserId,
            @Param("status") StudentGuardianRelationshipStatus status
    );
}
