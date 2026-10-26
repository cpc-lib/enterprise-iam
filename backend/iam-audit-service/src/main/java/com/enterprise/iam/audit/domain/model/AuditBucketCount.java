package com.enterprise.iam.audit.domain.model;

import java.util.Objects;

public record AuditBucketCount(String key, long count) {

    public AuditBucketCount {
        Objects.requireNonNull(key, "key must not be null");
    }
}
