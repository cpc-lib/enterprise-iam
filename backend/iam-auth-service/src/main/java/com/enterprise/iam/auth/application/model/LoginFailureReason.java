package com.enterprise.iam.auth.application.model;

/** Internal-only classification; never returned in the public login response. */
public enum LoginFailureReason {
    UNKNOWN_IDENTITY,
    INACTIVE_IDENTITY,
    MISSING_CREDENTIAL,
    INACTIVE_CREDENTIAL,
    LOCKED_CREDENTIAL,
    WRONG_PASSWORD
}
