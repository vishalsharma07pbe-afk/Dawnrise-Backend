package com.edusphere.identity.platform.auth.activation.notification;

import com.edusphere.identity.platform.user.entity.PlatformUser;

public interface PlatformActivationLinkSender {

    void sendActivationLink(
            PlatformUser platformUser,
            String rawToken
    );
}