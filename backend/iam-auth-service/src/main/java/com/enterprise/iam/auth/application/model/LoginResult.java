package com.enterprise.iam.auth.application.model;

import java.util.Optional;

public record LoginResult(boolean authenticated, String publicCode, IssuedLoginSession session) {

    public static final String SUCCESS_CODE = "OK";
    public static final String FAILURE_CODE = "IAM_AUTHENTICATION_FAILED";

    public LoginResult {
        if (authenticated != (session != null)) {
            throw new IllegalArgumentException("authenticated and session must be consistent");
        }
        String expected = authenticated ? SUCCESS_CODE : FAILURE_CODE;
        if (!expected.equals(publicCode)) {
            throw new IllegalArgumentException("publicCode differs from the frozen public outcome");
        }
    }

    public static LoginResult authenticated(IssuedLoginSession session) {
        return new LoginResult(true, SUCCESS_CODE, session);
    }

    public static LoginResult rejected() {
        return new LoginResult(false, FAILURE_CODE, null);
    }

    public Optional<IssuedLoginSession> issuedSession() {
        return Optional.ofNullable(session);
    }

    @Override
    public String toString() {
        return "LoginResult[authenticated=" + authenticated + ", publicCode="
                + publicCode + ", session=" + (session == null ? "null" : "REDACTED") + "]";
    }
}
