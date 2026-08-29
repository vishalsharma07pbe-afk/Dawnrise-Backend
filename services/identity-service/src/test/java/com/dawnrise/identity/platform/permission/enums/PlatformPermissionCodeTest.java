package com.dawnrise.identity.platform.permission.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformPermissionCodeTest {

    @Test
    void provisioningUpdatePermissionCode_isAvailableForJwtResolution() {
        assertEquals(
                PlatformPermissionCode.PROVISIONING_UPDATE,
                PlatformPermissionCode.valueOf("PROVISIONING_UPDATE")
        );
    }
}
