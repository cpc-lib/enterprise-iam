package com.enterprise.iam.sharing.interfaces.rest.dto;

import com.enterprise.iam.sharing.domain.model.ShareScopeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Frozen OpenAPI {@code ReshareRequest}: the create-share shape plus the
 * optimistic parent version fence and V2.0 scope parameters.
 */
public record ReshareRequest(
        @NotNull @Positive Long resourceId,
        @NotBlank @Size(max = 128) String resourceInstanceKey,
        @NotNull @Valid CreateShareRequest.Target target,
        @NotEmpty @Size(max = 100) List<@Positive Long> operationIds,
        @NotNull @Size(max = 1000) @Valid List<CreateShareRequest.FieldGrant> fields,
        @NotNull Instant expireAt,
        boolean canReshare,
        @NotNull @PositiveOrZero Long parentVersion,
        ShareScopeType scopeType,
        @Size(max = 512) String folderPath,
        @Size(max = 500) List<@NotBlank String> collectionResourceIds) {

    public ReshareRequest {
        scopeType = scopeType == null ? ShareScopeType.INSTANCE : scopeType;
        collectionResourceIds = collectionResourceIds == null
                ? List.of() : List.copyOf(collectionResourceIds);
    }

    public ReshareRequest(
            Long resourceId,
            String resourceInstanceKey,
            CreateShareRequest.Target target,
            List<Long> operationIds,
            List<CreateShareRequest.FieldGrant> fields,
            Instant expireAt,
            boolean canReshare,
            Long parentVersion) {
        this(resourceId, resourceInstanceKey, target, operationIds, fields,
                expireAt, canReshare, parentVersion, ShareScopeType.INSTANCE, null, List.of());
    }
}
