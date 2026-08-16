package com.enterprise.iam.outbox;

public enum OutboxRelayOutcome {
    PUBLISHED,
    RETRY_SCHEDULED,
    DEAD,
    UNSUPPORTED,
    LEASE_LOST
}
