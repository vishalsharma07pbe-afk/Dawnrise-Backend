package com.edusphere.identity.platform.audit.service;

import com.edusphere.identity.platform.audit.entity.PlatformSecurityAuditEvent;
import com.edusphere.identity.platform.audit.repository.PlatformSecurityAuditEventRepository;
import com.edusphere.identity.securityaudit.enums.SecurityAuditAction;
import com.edusphere.identity.securityaudit.enums.SecurityAuditOutcome;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PlatformSecurityAuditServiceImpl
        implements PlatformSecurityAuditService {

    private static final Set<String> SECRET_DETAIL_KEYS = Set.of(
            "password",
            "passwordHash",
            "accessToken",
            "refreshToken",
            "activationToken",
            "passwordResetToken",
            "token"
    );

    private final PlatformSecurityAuditEventRepository repository;

    public PlatformSecurityAuditServiceImpl(
            PlatformSecurityAuditEventRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Long actorPlatformUserId,
            SecurityAuditAction action,
            SecurityAuditOutcome outcome,
            String targetType,
            Long targetId,
            Map<String, ?> details
    ) {
        repository.save(new PlatformSecurityAuditEvent(
                actorPlatformUserId,
                action,
                outcome,
                targetType,
                targetId,
                currentRequestId(),
                currentIpAddress(),
                sanitize(details)
        ));
    }

    private String sanitize(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }

        return details.entrySet()
                .stream()
                .filter(entry ->
                        !SECRET_DETAIL_KEYS.contains(entry.getKey())
                )
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private String currentRequestId() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }

        String requestId = request.getHeader("X-Request-ID");
        return requestId == null || requestId.isBlank()
                ? request.getHeader("X-Correlation-ID")
                : requestId;
    }

    private String currentIpAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes)) {
            return null;
        }

        return attributes.getRequest();
    }
}
