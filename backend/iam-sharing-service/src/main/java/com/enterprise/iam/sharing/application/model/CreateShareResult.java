package com.enterprise.iam.sharing.application.model;

import com.enterprise.iam.sharing.domain.model.ShareScopeType;
import com.enterprise.iam.sharing.domain.model.ShareStatus;

import java.util.List;

public record CreateShareResult(
        long shareId,
        ShareStatus status,
        long version,
        ShareScopeType scopeType,
        String folderPath,
        List<String> collectionResourceIds) {

    public CreateShareResult {
        scopeType = scopeType == null ? ShareScopeType.INSTANCE : scopeType;
        collectionResourceIds = collectionResourceIds == null
                ? List.of() : List.copyOf(collectionResourceIds);
    }

    public CreateShareResult(long shareId, ShareStatus status, long version) {
        this(shareId, status, version, ShareScopeType.INSTANCE, null, List.of());
    }
}
