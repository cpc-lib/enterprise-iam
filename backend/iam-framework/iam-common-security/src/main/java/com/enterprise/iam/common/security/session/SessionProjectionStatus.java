package com.enterprise.iam.common.security.session;

/** Security-relevant lifecycle values persisted in the Redis projection. */
public enum SessionProjectionStatus {
    ACTIVE,
    REVOKED,
    EXPIRED
}
