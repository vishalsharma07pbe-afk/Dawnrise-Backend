package com.dawnrise.school.school.repository;

import com.dawnrise.school.school.entity.School;
import com.dawnrise.school.school.enums.SchoolStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface schoolRepository extends JpaRepository<School, Long> {
    boolean existsBySchoolCode(String schoolCode);
    Page<School> findAllByStatus(SchoolStatus status, Pageable pageable);
    @Query("""
        SELECT school
        FROM School school
        WHERE school.status = :status
          AND (
                LOWER(school.name)
                    LIKE LOWER(CONCAT('%', :search, '%'))
                OR
                LOWER(school.schoolCode)
                    LIKE LOWER(CONCAT('%', :search, '%'))
          )
        """)
    Page<School> searchByStatus(
            @Param("status") SchoolStatus status,
            @Param("search") String search,
            Pageable pageable
);
}
