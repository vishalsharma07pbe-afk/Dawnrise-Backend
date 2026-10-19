package com.dawnrise.academic.reportcard.repository;

import com.dawnrise.academic.reportcard.entity.ReportCard;
import com.dawnrise.academic.reportcard.enums.ReportCardStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface ReportCardRepository extends JpaRepository<ReportCard, Long> {
    @EntityGraph(attributePaths = "subjects")
    Optional<ReportCard> findByOrganizationIdAndPublicationIdAndStudentUserId(Long o, Long p, Long s);

    @EntityGraph(attributePaths = "subjects")
    List<ReportCard> findAllByOrganizationIdAndPublicationIdOrderByRollNumberAscIdAsc(Long o, Long p);

    long countByOrganizationIdAndPublicationId(Long o, Long p);

    @EntityGraph(attributePaths = "subjects")
    Optional<ReportCard> findByIdAndOrganizationId(Long id, Long o);

    @EntityGraph(attributePaths = "subjects")
    Optional<ReportCard> findByIdAndOrganizationIdAndStatus(Long id, Long o, ReportCardStatus s);

    @EntityGraph(attributePaths = "subjects")
    List<ReportCard> findAllByOrganizationIdAndStudentUserIdAndStatusOrderByPublishedAtDescIdDesc(Long o, Long s, ReportCardStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "subjects")
    @Query("select c from ReportCard c where c.id=:id and c.organizationId=:o")
    Optional<ReportCard> findForUpdate(@Param("id") Long id, @Param("o") Long o);
}
