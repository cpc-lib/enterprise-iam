package com.enterprise.iam.auth.application.model;

import java.util.Objects;

public record AuthenticationAttempt(
        boolean successful,
        Long tenantId,
        Long userId,
        LoginFailureReason failureReason,
        String requestId) {

    public AuthenticationAttempt {
        if (successful == (failureReason != null)) {
            throw new IllegalArgumentException("success and failureReason must be consistent");
        }
        if (tenantId != null && tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive when present");
        }
        if (userId != null && userId <= 0) {
            throw new IllegalArgumentException("userId must be positive when present");
        }
        Objects.requireNonNull(requestId, "requestId must not be null");
        if (requestId.isBlank() || requestId.length() > 128) {
            throw new IllegalArgumentException("requestId must be non-blank and at most 128 characters");
        }
    }
}
