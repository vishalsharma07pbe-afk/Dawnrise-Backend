package com.dawnrise.identity.platform.auth.passwordreset.service;

import com.dawnrise.identity.auth.passwordreset.dto.CompletePasswordResetRequest;
import com.dawnrise.identity.platform.auth.passwordreset.dto.PlatformPasswordResetRequest;

public interface PlatformPasswordResetService {
    void requestReset(PlatformPasswordResetRequest request);
    boolean isTokenValid(String token);
    void completeReset(CompletePasswordResetRequest request);
}
