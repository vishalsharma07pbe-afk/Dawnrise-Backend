package com.dawnrise.identity.notification.sms;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSmsPropertiesTest {
    @Test
    void remainsDisabledWithoutCredentialsAndApprovedTemplate() {
        NotificationSmsProperties properties = new NotificationSmsProperties();
        properties.setEnabled(true);
        properties.setTemplateIds(Map.of("SCHOOL_ANNOUNCEMENT", ""));
        assertThat(properties.configured()).isFalse();
    }

    @Test
    void isConfiguredOnlyWhenEnabledWithCredentialsAndTemplate() {
        NotificationSmsProperties properties = new NotificationSmsProperties();
        properties.setEnabled(true);
        properties.setAuthKey("secret");
        properties.setTemplateIds(Map.of("SCHOOL_ANNOUNCEMENT", "approved-template"));
        assertThat(properties.configured()).isTrue();
    }
}
