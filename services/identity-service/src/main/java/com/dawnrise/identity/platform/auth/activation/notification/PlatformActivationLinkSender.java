package com.dawnrise.identity.platform.auth.activation.notification;

import com.dawnrise.identity.platform.user.entity.PlatformUser;

public interface PlatformActivationLinkSender {

    void sendActivationLink(
            PlatformUser platformUser,
            String rawToken
    );
}