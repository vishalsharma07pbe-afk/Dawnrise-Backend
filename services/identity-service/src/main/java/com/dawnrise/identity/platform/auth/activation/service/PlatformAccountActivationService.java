package com.dawnrise.identity.platform.auth.activation.service;

import com.dawnrise.identity.auth.activation.dto.CompleteAccountActivationRequest;
import com.dawnrise.identity.auth.activation.dto.ResendActivationRequest;

public interface PlatformAccountActivationService {

    String generateActivationToken(
            Long platformUserId
    );

    boolean isActivationTokenValid(
            String rawToken
    );

    void completeActivation(
            CompleteAccountActivationRequest request
    );

    void requestActivationResend(
            ResendActivationRequest request
    );
}