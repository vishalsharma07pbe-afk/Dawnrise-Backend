package com.dawnrise.academic.studentattendance.correction.repository;

import com.dawnrise.academic.studentattendance.correction.entity.StudentAttendanceCorrectionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StudentAttendanceCorrectionItemRepository
        extends JpaRepository<StudentAttendanceCorrectionItem, Long> {

    List<StudentAttendanceCorrectionItem> findAllByCorrectionRequestIdOrderByIdAsc(Long correctionRequestId);

    List<StudentAttendanceCorrectionItem> findAllByCorrectionRequestIdInOrderByCorrectionRequestIdAscIdAsc(
            Collection<Long> correctionRequestIds
    );
}
