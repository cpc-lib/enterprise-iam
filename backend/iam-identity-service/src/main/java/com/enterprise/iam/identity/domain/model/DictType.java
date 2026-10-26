package com.enterprise.iam.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

public record DictType(
        long id,
        long tenantId,
        String dictType,
        String dictName,
        DictStatus status,
        String description,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public DictType {
        Objects.requireNonNull(dictType, "dictType must not be null");
        Objects.requireNonNull(dictName, "dictName must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
