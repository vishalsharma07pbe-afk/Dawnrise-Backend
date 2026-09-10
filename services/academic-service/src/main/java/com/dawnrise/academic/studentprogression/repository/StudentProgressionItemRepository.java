package com.dawnrise.academic.studentprogression.repository;

import com.dawnrise.academic.studentprogression.entity.StudentProgressionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;

import java.util.List;

public interface StudentProgressionItemRepository
        extends JpaRepository<StudentProgressionItem, Long> {

    boolean existsByOrganizationIdAndSourceEnrollmentId(
            Long organizationId,
            Long sourceEnrollmentId
    );

    List<StudentProgressionItem>
    findAllByOperationIdAndOrganizationIdOrderByIdAsc(
            Long operationId,
            Long organizationId
    );

    List<StudentProgressionItem>
    findAllByOrganizationIdAndStudentUserIdOrderByCreatedAtDesc(
            Long organizationId,
            Long studentUserId
    );

    List<StudentProgressionItem>
    findAllByOrganizationIdAndSourceEnrollmentIdIn(
            Long organizationId,
            Collection<Long> sourceEnrollmentIds
    );
}