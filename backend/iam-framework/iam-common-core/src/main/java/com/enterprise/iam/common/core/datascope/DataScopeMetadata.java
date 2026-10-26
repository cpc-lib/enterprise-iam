package com.enterprise.iam.common.core.datascope;

import java.util.List;
import java.util.Objects;

/**
 * Resolved data-scope obligations for a single resource type. An empty list
 * means no scope was granted: row-level access fails closed.
 */
public record DataScopeMetadata(String resourceType, List<DataScopeObligation> scopes) {

    public DataScopeMetadata {
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        if (resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType must not be blank");
        }
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
}
