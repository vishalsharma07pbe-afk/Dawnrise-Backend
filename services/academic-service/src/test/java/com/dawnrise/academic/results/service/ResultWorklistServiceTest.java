package com.dawnrise.academic.results.service;

import com.dawnrise.academic.results.enums.ResultSubjectSheetStatus;
import com.dawnrise.academic.results.repository.ResultWorklistProjection;
import com.dawnrise.academic.results.repository.ResultWorklistQueryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResultWorklistServiceTest {
    private final ResultWorklistQueryRepository worklists =
            mock(ResultWorklistQueryRepository.class);
    private final ResultWorklistService service =
            new ResultWorklistService(worklists);

    @Test
    void subjectTeacherGetsMarkEntryTasksIncludingUncreatedSheet() {
        when(worklists.findSubjectTeacherTasks(1L, 100L))
                .thenReturn(List.of(task("MARK_ENTRY", null, null, 30, 0)));

        var response = service.list(1, 100, true, false);

        assertEquals(1, response.size());
        assertEquals("MARK_ENTRY", response.getFirst().taskType());
        assertEquals("NOT_CREATED", response.getFirst().status());
        assertEquals(null, response.getFirst().subjectSheetId());
        assertEquals(30L, response.getFirst().totalStudents());
        assertEquals(0L, response.getFirst().completedMarks());
        verify(worklists).findSubjectTeacherTasks(1L, 100L);
        verify(worklists, never()).findClassTeacherTasks(1L, 100L);
    }

    @Test
    void classTeacherGetsReviewTasksWithSheetStatusAndCounts() {
        when(worklists.findClassTeacherTasks(1L, 200L))
                .thenReturn(List.of(task("REVIEW", 50L,
                        ResultSubjectSheetStatus.SUBMITTED, 28, 28)));

        var response = service.list(1, 200, false, true);

        assertEquals(1, response.size());
        assertEquals("REVIEW", response.getFirst().taskType());
        assertEquals(50L, response.getFirst().subjectSheetId());
        assertEquals("SUBMITTED", response.getFirst().status());
        assertEquals(28L, response.getFirst().totalStudents());
        assertEquals(28L, response.getFirst().completedMarks());
        verify(worklists, never()).findSubjectTeacherTasks(1L, 200L);
        verify(worklists).findClassTeacherTasks(1L, 200L);
    }

    @Test
    void missingAssignmentReturnsNoTasks() {
        when(worklists.findSubjectTeacherTasks(1L, 300L)).thenReturn(List.of());
        when(worklists.findClassTeacherTasks(1L, 300L)).thenReturn(List.of());

        var response = service.list(1, 300, true, true);

        assertEquals(List.of(), response);
    }

    @Test
    void crossTenantAccessUsesAuthenticatedOrganizationScope() {
        when(worklists.findSubjectTeacherTasks(9L, 100L))
                .thenReturn(List.of(task("MARK_ENTRY", null, null, 10, 0)));

        service.list(9, 100, true, false);

        verify(worklists).findSubjectTeacherTasks(9L, 100L);
        verify(worklists, never()).findSubjectTeacherTasks(1L, 100L);
    }

    private static ResultWorklistProjection task(String type, Long sheetId,
                                                 ResultSubjectSheetStatus status,
                                                 long totalStudents,
                                                 long completedMarks) {
        return new ResultWorklistProjection(type, 2L, "2026-27",
                3L, "Mid-term", 6L,
                LocalDate.of(2026, 10, 5), BigDecimal.valueOf(50),
                4L, "G4", "Grade 4", 7L, "A", "Section A",
                5L, 8L, "MATH", "Mathematics", sheetId, status,
                sheetId == null ? null : 1L, totalStudents, completedMarks);
    }
}
