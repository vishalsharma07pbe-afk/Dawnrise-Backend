package com.dawnrise.identity.auth.passwordreset.notification;

import com.dawnrise.identity.user.entity.User;

public interface PasswordResetLinkSender {

    void sendPasswordResetLink(User user, String rawToken);
}
