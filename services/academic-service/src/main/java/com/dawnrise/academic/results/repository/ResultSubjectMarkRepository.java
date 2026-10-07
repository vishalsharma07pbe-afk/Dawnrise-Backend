package com.dawnrise.academic.results.repository;

import com.dawnrise.academic.results.entity.ResultSubjectMark;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResultSubjectMarkRepository
        extends JpaRepository<ResultSubjectMark, Long> {

    List<ResultSubjectMark> findAllBySubjectSheetIdOrderByRollNumberSnapshotAscIdAsc(
            Long subjectSheetId
    );

    boolean existsBySubjectSheetId(Long subjectSheetId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT mark
            FROM ResultSubjectMark mark
            WHERE mark.subjectSheetId = :subjectSheetId
            ORDER BY mark.rollNumberSnapshot ASC, mark.id ASC
            """)
    List<ResultSubjectMark> findAllBySubjectSheetIdForUpdate(
            @Param("subjectSheetId") Long subjectSheetId
    );
}
