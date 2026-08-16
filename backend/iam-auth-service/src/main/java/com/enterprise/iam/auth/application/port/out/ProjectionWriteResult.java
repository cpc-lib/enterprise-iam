package com.enterprise.iam.auth.application.port.out;

/** Outcome of the idempotent, monotonic Redis projection publication. */
public enum ProjectionWriteResult {
    APPLIED,
    STALE_IGNORED
}
