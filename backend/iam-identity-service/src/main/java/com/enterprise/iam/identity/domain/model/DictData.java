package com.enterprise.iam.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

public record DictData(
        long id,
        long tenantId,
        String dictType,
        String dictLabel,
        String dictValue,
        int sortOrder,
        DictStatus status,
        String cssTag,
        boolean isDefault,
        String description,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public DictData {
        Objects.requireNonNull(dictType, "dictType must not be null");
        Objects.requireNonNull(dictLabel, "dictLabel must not be null");
        Objects.requireNonNull(dictValue, "dictValue must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
