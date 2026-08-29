package com.dawnrise.identity.securityaudit.service;

import com.dawnrise.identity.common.dto.PageResponse;
import com.dawnrise.identity.securityaudit.dto.SecurityAuditEventResponse;
import com.dawnrise.identity.securityaudit.entity.SecurityAuditEvent;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;
import com.dawnrise.identity.securityaudit.mapper.SecurityAuditEventMapper;
import com.dawnrise.identity.securityaudit.repository.SecurityAuditEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SecurityAuditServiceImpl implements SecurityAuditService {

    private static final Set<String> SECRET_DETAIL_KEYS = Set.of(
            "password",
            "passwordHash",
            "accessToken",
            "refreshToken",
            "activationToken",
            "passwordResetToken",
            "token"
    );

    private final SecurityAuditEventRepository repository;
    private final SecurityAuditEventMapper mapper;

    public SecurityAuditServiceImpl(
            SecurityAuditEventRepository repository,
            SecurityAuditEventMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Long organizationId,
            Long actorUserId,
            SecurityAuditAction action,
            SecurityAuditOutcome outcome,
            String targetType,
            Long targetId,
            Map<String, ?> details
    ) {
        repository.save(new SecurityAuditEvent(
                organizationId,
                actorUserId,
                action,
                outcome,
                targetType,
                targetId,
                currentRequestId(),
                currentIpAddress(),
                sanitize(details)
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SecurityAuditEventResponse> getEvents(
            Long organizationId,
            Pageable pageable
    ) {
        return PageResponse.from(
                repository
                        .findAllByOrganizationId(
                                organizationId,
                                pageable
                        )
                        .map(mapper::toResponse)
        );
    }

    private String sanitize(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }

        return details.entrySet()
                .stream()
                .filter(entry -> !SECRET_DETAIL_KEYS.contains(entry.getKey()))
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
