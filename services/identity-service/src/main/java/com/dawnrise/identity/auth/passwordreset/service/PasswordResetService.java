package com.dawnrise.identity.auth.passwordreset.service;

import com.dawnrise.identity.auth.passwordreset.dto.CompletePasswordResetRequest;
import com.dawnrise.identity.auth.passwordreset.dto.PasswordResetRequest;

public interface PasswordResetService {

    void requestPasswordReset(PasswordResetRequest request);

    String generatePasswordResetToken(Long userId);

    boolean isPasswordResetTokenValid(String rawToken);

    void completePasswordReset(CompletePasswordResetRequest request);
}
