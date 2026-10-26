package com.enterprise.iam.authorization.domain.model;

/**
 * Security epoch fence for one (tenant, resource): while
 * {@code expected > applied} the projection is behind the sharing service and
 * the engine must fail closed.
 */
public record ShareEpoch(long expectedEpoch, long appliedEpoch) {

    public ShareEpoch {
        if (expectedEpoch < 0 || appliedEpoch < 0) {
            throw new IllegalArgumentException("epochs must not be negative");
        }
        if (appliedEpoch > expectedEpoch) {
            throw new IllegalArgumentException("appliedEpoch must not exceed expectedEpoch");
        }
    }

    public boolean fresh() {
        return expectedEpoch == appliedEpoch;
    }
}
