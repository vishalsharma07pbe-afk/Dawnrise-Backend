package com.dawnrise.academic.studentattendance.correction.mapper;

import com.dawnrise.academic.studentattendance.correction.dto.StudentAttendanceCorrectionItemResponse;
import com.dawnrise.academic.studentattendance.correction.dto.StudentAttendanceCorrectionResponse;
import com.dawnrise.academic.studentattendance.correction.entity.StudentAttendanceCorrectionItem;
import com.dawnrise.academic.studentattendance.correction.entity.StudentAttendanceCorrectionRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StudentAttendanceCorrectionMapper {

    public StudentAttendanceCorrectionResponse toResponse(
            StudentAttendanceCorrectionRequest request,
            List<StudentAttendanceCorrectionItem> items
    ) {
        return new StudentAttendanceCorrectionResponse(
                request.getId(),
                request.getOrganizationId(),
                request.getAcademicYearId(),
                request.getGradeLevelId(),
                request.getSectionId(),
                request.getAttendanceSessionId(),
                request.getReason(),
                request.getStatus(),
                request.getRequestedByUserId(),
                request.getReviewedByUserId(),
                request.getReviewComment(),
                request.getRequestedAt(),
                request.getReviewedAt(),
                request.getVersion(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                items.stream().map(this::toResponse).toList()
        );
    }

    public StudentAttendanceCorrectionItemResponse toResponse(StudentAttendanceCorrectionItem item) {
        return new StudentAttendanceCorrectionItemResponse(
                item.getId(),
                item.getAttendanceRecordId(),
                item.getStudentEnrollmentId(),
                item.getStudentUserId(),
                item.getExpectedAttendanceRecordVersion(),
                item.getPreviousRecordedStatus(),
                item.getPreviousEffectiveStatus(),
                item.getPreviousEarnedCredit(),
                item.getPreviousPossibleCredit(),
                item.isPreviousLatePenaltyApplied(),
                item.getPreviousRemarks(),
                item.getProposedRecordedStatus(),
                item.getProposedEffectiveStatus(),
                item.getProposedEarnedCredit(),
                item.getProposedPossibleCredit(),
                item.isProposedLatePenaltyApplied(),
                item.getProposedRemarks(),
                item.getCreatedAt()
        );
    }
}
