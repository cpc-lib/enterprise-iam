package com.enterprise.iam.auth.application.model;

import java.util.Objects;

public record IssuedLoginSession(
        String accessToken,
        long expiresIn,
        SensitiveRefreshToken refreshToken,
        long refreshExpiresIn,
        long sessionId,
        long userId,
        long tenantId) {

    public IssuedLoginSession {
        accessToken = requireText(accessToken, "accessToken");
        requirePositive(expiresIn, "expiresIn");
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");
        requirePositive(refreshExpiresIn, "refreshExpiresIn");
        requirePositive(sessionId, "sessionId");
        requirePositive(userId, "userId");
        requirePositive(tenantId, "tenantId");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    @Override
    public String toString() {
        return "IssuedLoginSession[accessToken=REDACTED, expiresIn=" + expiresIn
                + ", refreshToken=REDACTED, refreshExpiresIn=" + refreshExpiresIn
                + ", sessionId=" + sessionId + ", userId=" + userId
                + ", tenantId=" + tenantId + "]";
    }
}
