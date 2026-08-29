package com.dawnrise.identity.organization.provisioning.security;

import com.dawnrise.identity.organization.provisioning.dto.InitialAuthorityRequest;
import com.dawnrise.identity.organization.provisioning.dto.ProvisionOrganizationRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class ProvisioningRequestHasher {

    private static final String HASH_ALGORITHM =
            "SHA-256";

    private static final String HASH_FORMAT_VERSION =
            "v1";

    public String hash(
            ProvisionOrganizationRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Provisioning request is required"
            );
        }

        InitialAuthorityRequest authority =
                request.getAuthority();

        if (authority == null) {
            throw new IllegalArgumentException(
                    "Initial authority information is required"
            );
        }

        StringBuilder canonicalRequest =
                new StringBuilder();

        append(
                canonicalRequest,
                HASH_FORMAT_VERSION
        );

        append(
                canonicalRequest,
                String.valueOf(request.getOrganizationId())
        );

        append(
                canonicalRequest,
                normalizeRequired(request.getSchoolCode())
        );

        append(
                canonicalRequest,
                normalizeRequired(request.getSchoolName())
        );

        append(
                canonicalRequest,
                normalizeEmail(request.getSchoolEmail())
        );

        append(
                canonicalRequest,
                normalizeRequired(authority.getUsername())
        );

        append(
                canonicalRequest,
                normalizeRequired(authority.getFirstName())
        );

        append(
                canonicalRequest,
                normalizeOptional(authority.getMiddleName())
        );

        append(
                canonicalRequest,
                normalizeRequired(authority.getLastName())
        );

        append(
                canonicalRequest,
                normalizeEmail(authority.getEmail())
        );

        append(
                canonicalRequest,
                normalizeOptional(authority.getPhone())
        );

        return sha256(
                canonicalRequest.toString()
        );
    }

    private void append(
            StringBuilder builder,
            String value
    ) {
        if (value == null) {
            builder.append("-1:|");
            return;
        }

        builder
                .append(value.length())
                .append(':')
                .append(value)
                .append('|');
    }

    private String normalizeRequired(
            String value
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Required provisioning value is missing"
            );
        }

        return value.trim();
    }

    private String normalizeOptional(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String normalizeEmail(
            String value
    ) {
        String normalized =
                normalizeOptional(value);

        if (normalized == null) {
            return null;
        }

        return normalized.toLowerCase(
                Locale.ROOT
        );
    }

    private String sha256(
            String canonicalRequest
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            HASH_ALGORITHM
                    );

            byte[] hash = digest.digest(
                    canonicalRequest.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            return HexFormat
                    .of()
                    .formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception
            );
        }
    }
}