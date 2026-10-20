package com.dawnrise.academic.grading.service;

import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.entity.GradeLevel;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.grading.dto.GradeBandRequest;
import com.dawnrise.academic.grading.dto.GradingPolicyRequest;
import com.dawnrise.academic.grading.entity.GradingPolicy;
import com.dawnrise.academic.grading.enums.GradingPolicyStatus;
import com.dawnrise.academic.grading.repository.GradingPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GradingPolicyServiceTest {
    @Mock
    private GradingPolicyRepository policies;
    @Mock
    private AcademicYearRepository years;
    @Mock
    private GradeLevelRepository grades;
    private GradingPolicyService service;

    @BeforeEach
    void setUp() {
        service = new GradingPolicyService(policies, years, grades);
    }

    @Test
    void firstActivationAssignsVersionOneUnderScopeLock() throws Exception {
        GradingPolicy draft = draft(10);
        arrangeActivation(draft, List.of(draft));

        service.activate(1, 9, 10);

        assertEquals(1, draft.getPolicyVersion());
        assertEquals(GradingPolicyStatus.ACTIVE, draft.getStatus());
        verify(grades).findScopeForUpdate(3L, 2L, 1L);
        verify(policies).lockScope(1L, 2L, 3L);
    }

    @Test
    void replacementActivationRetiresActiveAndUsesNextScopeVersion() throws Exception {
        GradingPolicy active = draft(10);
        active.activate(7, 1);
        GradingPolicy replacement = draft(11);
        arrangeActivation(replacement, List.of(active, replacement));

        service.activate(1, 9, 11);

        assertEquals(GradingPolicyStatus.RETIRED, active.getStatus());
        assertEquals(GradingPolicyStatus.ACTIVE, replacement.getStatus());
        assertEquals(2, replacement.getPolicyVersion());
    }

    @Test
    void versionAllocationOccursOnlyAfterDeterministicGradeScopeLock() throws Exception {
        GradingPolicy draft = draft(12);
        arrangeActivation(draft, List.of(draft));

        service.activate(1, 9, 12);

        var order = inOrder(grades, policies);
        order.verify(grades).findScopeForUpdate(3L, 2L, 1L);
        order.verify(policies).lockScope(1L, 2L, 3L);
        order.verify(policies).saveAndFlush(draft);
    }

    @Test
    void deletesDraftWhenVersionMatches() throws Exception {
        GradingPolicy draft = draft(13);
        when(policies.findByIdAndOrganizationId(13L, 1L)).thenReturn(Optional.of(draft));

        service.deleteDraft(1, 13, 0);

        verify(policies).delete(draft);
        verify(policies).flush();
    }

    @Test
    void refusesToDeleteActivePolicy() throws Exception {
        GradingPolicy active = draft(14);
        active.activate(9, 1);
        when(policies.findByIdAndOrganizationId(14L, 1L)).thenReturn(Optional.of(active));

        assertThrows(RuntimeException.class, () -> service.deleteDraft(1, 14, 0));

        verify(policies, never()).delete(any());
    }

    @Test
    void refusesToDeleteStaleDraft() throws Exception {
        GradingPolicy draft = draft(15);
        when(policies.findByIdAndOrganizationId(15L, 1L)).thenReturn(Optional.of(draft));

        assertThrows(RuntimeException.class, () -> service.deleteDraft(1, 15, 1));

        verify(policies, never()).delete(any());
    }

    private void arrangeActivation(GradingPolicy draft, List<GradingPolicy> scope) {
        when(policies.findByIdAndOrganizationId(draft.getId(), 1L)).thenReturn(Optional.of(draft));
        when(grades.findScopeForUpdate(3L, 2L, 1L)).thenReturn(Optional.of(mock(GradeLevel.class)));
        when(policies.lockScope(1L, 2L, 3L)).thenReturn(scope);
        when(policies.saveAndFlush(draft)).thenReturn(draft);
    }

    private static GradingPolicy draft(long id) throws Exception {
        GradingPolicy policy = new GradingPolicy(1, 9, new GradingPolicyRequest(
                2, 3, "Policy " + id, null, new BigDecimal("40.00"),
                new BigDecimal("40.00"), true, true, 2, RoundingMode.HALF_UP,
                List.of(new GradeBandRequest(
                        "All", new BigDecimal("0.00"), new BigDecimal("100.00"), 1, null)),
                null));
        set(policy, "id", id);
        set(policy, "version", 0L);
        return policy;
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
