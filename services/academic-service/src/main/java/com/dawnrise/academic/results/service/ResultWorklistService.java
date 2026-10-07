package com.dawnrise.academic.results.service;

import com.dawnrise.academic.results.dto.ResultWorklistTaskResponse;
import com.dawnrise.academic.results.repository.ResultWorklistProjection;
import com.dawnrise.academic.results.repository.ResultWorklistQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class ResultWorklistService {
    private final ResultWorklistQueryRepository worklists;

    public ResultWorklistService(ResultWorklistQueryRepository worklists) {
        this.worklists = worklists;
    }

    public List<ResultWorklistTaskResponse> list(long organizationId,
                                                 long actorUserId,
                                                 boolean canEnterMarks,
                                                 boolean canReview) {
        Stream<ResultWorklistProjection> subjectTasks = canEnterMarks
                ? worklists.findSubjectTeacherTasks(organizationId, actorUserId).stream()
                : Stream.empty();
        Stream<ResultWorklistProjection> reviewTasks = canReview
                ? worklists.findClassTeacherTasks(organizationId, actorUserId).stream()
                : Stream.empty();
        return Stream.concat(subjectTasks, reviewTasks)
                .map(ResultWorklistService::response)
                .sorted(Comparator
                        .comparing(ResultWorklistTaskResponse::assessmentDate)
                        .thenComparing(ResultWorklistTaskResponse::examinationId)
                        .thenComparing(ResultWorklistTaskResponse::gradeLevelId)
                        .thenComparing(ResultWorklistTaskResponse::sectionId)
                        .thenComparing(ResultWorklistTaskResponse::gradeLevelSubjectId)
                        .thenComparing(ResultWorklistTaskResponse::taskType))
                .toList();
    }

    private static ResultWorklistTaskResponse response(
            ResultWorklistProjection projection) {
        return new ResultWorklistTaskResponse(
                projection.taskType(),
                projection.academicYearId(),
                projection.academicYearName(),
                projection.examinationId(),
                projection.examinationName(),
                projection.scheduledAssessmentId(),
                projection.assessmentDate(),
                projection.maximumMarks(),
                projection.gradeLevelId(),
                projection.gradeLevelCode(),
                projection.gradeLevelName(),
                projection.sectionId(),
                projection.sectionCode(),
                projection.sectionName(),
                projection.gradeLevelSubjectId(),
                projection.subjectId(),
                projection.subjectCode(),
                projection.subjectName(),
                projection.subjectSheetId(),
                projection.sheetStatus() == null
                        ? "NOT_CREATED"
                        : projection.sheetStatus().name(),
                projection.sheetVersion(),
                projection.totalStudents(),
                projection.completedMarks());
    }
}
