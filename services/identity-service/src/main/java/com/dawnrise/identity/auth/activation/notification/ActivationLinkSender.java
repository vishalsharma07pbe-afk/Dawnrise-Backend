package com.dawnrise.identity.auth.activation.notification;

import com.dawnrise.identity.user.entity.User;

public interface ActivationLinkSender {

    void sendActivationLink(
            User user,
            String rawToken
    );
}