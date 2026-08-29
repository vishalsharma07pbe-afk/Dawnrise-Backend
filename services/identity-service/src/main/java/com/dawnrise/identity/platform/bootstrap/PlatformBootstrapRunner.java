package com.dawnrise.identity.platform.bootstrap;

import com.dawnrise.identity.platform.bootstrap.config.PlatformBootstrapProperties;
import com.dawnrise.identity.platform.bootstrap.service.PlatformBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PlatformBootstrapRunner
        implements ApplicationRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    PlatformBootstrapRunner.class
            );

    private final PlatformBootstrapProperties bootstrapProperties;
    private final PlatformBootstrapService bootstrapService;

    public PlatformBootstrapRunner(
            PlatformBootstrapProperties bootstrapProperties,
            PlatformBootstrapService bootstrapService
    ) {
        this.bootstrapProperties = bootstrapProperties;
        this.bootstrapService = bootstrapService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!bootstrapProperties.isEnabled()) {
            return;
        }

        PlatformBootstrapResult result =
                bootstrapService.bootstrapFirstSuperAdmin();

        switch (result) {
            case CREATED -> LOGGER.warn(
                    "The first platform Super Admin was created "
                            + "and is pending activation. "
                            + "Disable platform bootstrap after activation."
            );

            case ACTIVATION_REISSUED -> LOGGER.warn(
                    "A replacement activation email was requested "
                            + "for the pending first platform Super Admin. "
                            + "Disable platform bootstrap after activation."
            );

            case SKIPPED -> LOGGER.warn(
                    "Platform bootstrap was enabled but safely skipped. "
                            + "The existing platform-user state did not "
                            + "satisfy the strict bootstrap conditions."
            );
        }
    }
}