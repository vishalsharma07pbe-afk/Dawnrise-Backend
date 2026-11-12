package com.dawnrise.identity.notification.sms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSmsConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationSmsConfig.class);

    @Test
    void startsDisabledWithoutMsg91Credentials() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RestClient.class);
            assertThat(context).hasSingleBean(NotificationSmsProperties.class);
            assertThat(context.getBean(NotificationSmsProperties.class).configured()).isFalse();
        });
    }

    @Test
    void constructsMsg91ClientWhenEnabledAndConfigured() {
        contextRunner.withPropertyValues(
                        "dawnrise.notification-sms.enabled=true",
                        "dawnrise.notification-sms.auth-key=test-auth-key",
                        "dawnrise.notification-sms.template-ids.SCHOOL_ANNOUNCEMENT=test-template-id")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RestClient.class);
                    assertThat(context.getBean(NotificationSmsProperties.class).configured()).isTrue();
                });
    }
}
