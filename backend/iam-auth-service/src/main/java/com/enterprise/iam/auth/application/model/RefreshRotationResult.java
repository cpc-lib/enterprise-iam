package com.enterprise.iam.auth.application.model;

import java.util.Optional;

/**
 * Frozen public outcome of a refresh-token rotation attempt. Every rejection
 * renders the same public code; reuse detection is observable only through
 * durable side effects, never through the response body.
 */
public record RefreshRotationResult(boolean rotated, String publicCode, IssuedLoginSession session) {

    public static final String SUCCESS_CODE = "OK";
    public static final String FAILURE_CODE = "IAM_AUTHENTICATION_FAILED";

    public RefreshRotationResult {
        if (rotated != (session != null)) {
            throw new IllegalArgumentException("rotated and session must be consistent");
        }
        String expected = rotated ? SUCCESS_CODE : FAILURE_CODE;
        if (!expected.equals(publicCode)) {
            throw new IllegalArgumentException("publicCode differs from the frozen public outcome");
        }
    }

    public static RefreshRotationResult rotated(IssuedLoginSession session) {
        return new RefreshRotationResult(true, SUCCESS_CODE, session);
    }

    public static RefreshRotationResult rejected() {
        return new RefreshRotationResult(false, FAILURE_CODE, null);
    }

    public Optional<IssuedLoginSession> issuedSession() {
        return Optional.ofNullable(session);
    }

    @Override
    public String toString() {
        return "RefreshRotationResult[rotated=" + rotated + ", publicCode="
                + publicCode + ", session=" + (session == null ? "null" : "REDACTED") + "]";
    }
}
