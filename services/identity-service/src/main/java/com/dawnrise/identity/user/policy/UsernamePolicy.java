package com.dawnrise.identity.user.policy;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

public final class UsernamePolicy {

    public static final int MAX_LENGTH = 100;
    public static final int MAX_BASE_LENGTH = 90;
    private static final Set<String> RESERVED = Set.of(
            "admin", "root", "support", "system", "dawnrise"
    );

    private UsernamePolicy() {
    }

    public static String suggest(
            String firstName,
            String middleName,
            String lastName
    ) {
        String middleInitial = normalizePart(middleName);
        if (!middleInitial.isEmpty()) {
            middleInitial = middleInitial.substring(0, 1);
        }
        return normalizeBase(String.join(".",
                normalizePart(firstName),
                middleInitial,
                normalizePart(lastName)
        ));
    }

    public static String resolveAvailable(
            String requestedUsername,
            Predicate<String> exists
    ) {
        String base = normalizeBase(requestedUsername);
        if (!exists.test(base)) return base;

        for (int suffix = 2; suffix < 1_000_000_000; suffix++) {
            String suffixText = "." + suffix;
            int baseLimit = Math.min(MAX_BASE_LENGTH, MAX_LENGTH - suffixText.length());
            String candidate = trimSeparators(base.substring(0, Math.min(base.length(), baseLimit)))
                    + suffixText;
            if (!exists.test(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to allocate a unique username");
    }

    public static String normalizeBase(String value) {
        String normalized = normalizePart(value)
                .replaceAll("[._-]+", ".");
        normalized = trimSeparators(normalized);
        if (normalized.length() > MAX_BASE_LENGTH) {
            normalized = trimSeparators(normalized.substring(0, MAX_BASE_LENGTH));
        }
        if (normalized.length() < 3) {
            normalized = normalized.isEmpty() ? "user" : normalized + ".user";
        }
        if (RESERVED.contains(normalized)) {
            throw new IllegalArgumentException("This username is reserved and cannot be used");
        }
        return normalized;
    }

    private static String normalizePart(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("\\s+", ".")
                .replaceAll("[^a-z0-9._-]", "");
    }

    private static String trimSeparators(String value) {
        return value.replaceAll("^[._-]+|[._-]+$", "");
    }
}
