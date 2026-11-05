package com.dawnrise.academic.studentleave;

import com.dawnrise.academic.notificationdelivery.IdentityNotificationClient;
import com.dawnrise.academic.results.integration.identity.IdentityGuardianAccessClient;
import com.dawnrise.academic.studentleave.StudentLeaveDtos.Approval;
import com.dawnrise.academic.studentleave.StudentLeaveDtos.View;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentLeaveCancellationRecipientsTest {
    @Test
    void sendsCancellationToClassTeacherStudentInitiatorAndActiveGuardiansOnce() {
        var guardians = mock(IdentityGuardianAccessClient.class);
        when(guardians.activeGuardianUserIds(7, 15)).thenReturn(List.of(20L, 21L, 20L));
        @SuppressWarnings("unchecked")
        ObjectProvider<NamedParameterJdbcTemplate> database = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<IdentityNotificationClient> identities = mock(ObjectProvider.class);
        var service = new StudentLeaveService(database, guardians, identities);
        var request = new View(31, 15, 20, "GUARDIAN", 2, 3, 4, "APPROVED", 1,
                null, 2, 5, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4),
                null, null, null, null,
                List.of(new Approval(1, "CLASS_TEACHER", 40L, "APPROVED", null, null)), null);

        assertThat(service.cancellationRecipients(7, request)).isEqualTo(Set.of(15L, 20L, 21L, 40L));
    }
}
