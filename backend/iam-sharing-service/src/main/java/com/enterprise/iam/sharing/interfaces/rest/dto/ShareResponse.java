package com.enterprise.iam.sharing.interfaces.rest.dto;

import com.enterprise.iam.common.web.json.NumericLong;
import com.enterprise.iam.sharing.domain.model.ShareScopeType;
import com.enterprise.iam.sharing.domain.model.ShareStatus;

import java.util.List;

/**
 * Frozen OpenAPI {@code ShareResponse}. {@code shareId} is an identifier and is
 * serialized as a decimal string by the global web convention; the non-id
 * numeric quantities {@code version} and {@code projectionEpoch} opt out via
 * {@link NumericLong} so they remain JSON numbers.
 */
public record ShareResponse(
        long shareId,
        ShareStatus status,
        @NumericLong long version,
        @NumericLong long projectionEpoch,
        ShareScopeType scopeType,
        String folderPath,
        List<String> collectionResourceIds) {

    public ShareResponse {
        scopeType = scopeType == null ? ShareScopeType.INSTANCE : scopeType;
        collectionResourceIds = collectionResourceIds == null
                ? List.of() : List.copyOf(collectionResourceIds);
    }

    public ShareResponse(
            long shareId,
            ShareStatus status,
            long version,
            long projectionEpoch) {
        this(shareId, status, version, projectionEpoch, ShareScopeType.INSTANCE, null, List.of());
    }
}
