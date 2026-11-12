package com.dawnrise.identity.notification.sms;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationSmsDeliveryWorkerTest {
    private final NotificationSmsOutboxStore store = mock(NotificationSmsOutboxStore.class);
    private final Msg91SmsClient client = mock(Msg91SmsClient.class);
    private final NotificationSmsProperties properties = new NotificationSmsProperties();
    private final NotificationSmsDeliveryWorker worker = new NotificationSmsDeliveryWorker(
            store, client, properties, new ObjectMapper());

    @Test
    void doesNotClaimMessagesWhenProviderIsNotConfigured() {
        worker.deliver();
        verifyNoInteractions(store, client);
    }

    @Test
    void recordsProviderAcceptanceWithoutLoggingOrReturningCredentials() {
        configure();
        var item = new NotificationSmsOutboxStore.Item(UUID.randomUUID(), "+919876543210",
                "SCHOOL_ANNOUNCEMENT", "{\"VAR1\":\"Update\",\"VAR2\":\"Open Dawnrise\"}", 1);
        when(store.claim(any(), any(), any(), anyInt())).thenReturn(List.of(item));
        when(client.send(eq("template-id"), eq(item.phone()), eq(Map.of("VAR1", "Update", "VAR2", "Open Dawnrise"))))
                .thenReturn("provider-message-id");

        worker.deliver();

        verify(store).accepted(eq(item), any(), eq("provider-message-id"), any());
        verify(store, never()).failed(any(), any(), any(), anyInt(), any());
    }

    @Test
    void retriesProviderRejectionsThroughTheDurableOutbox() {
        configure();
        var item = new NotificationSmsOutboxStore.Item(UUID.randomUUID(), "+919876543210",
                "SCHOOL_ANNOUNCEMENT", "{\"VAR1\":\"Update\",\"VAR2\":\"Open Dawnrise\"}", 1);
        when(store.claim(any(), any(), any(), anyInt())).thenReturn(List.of(item));
        when(client.send(any(), any(), any())).thenThrow(new IllegalStateException("provider rejected request"));

        worker.deliver();

        verify(store).failed(eq(item), any(), any(), eq(5), any(IllegalStateException.class));
        verify(store, never()).accepted(any(), any(), any(), any());
    }

    private void configure() {
        properties.setEnabled(true);
        properties.setAuthKey("secret");
        properties.setTemplateIds(Map.of("SCHOOL_ANNOUNCEMENT", "template-id"));
    }
}
