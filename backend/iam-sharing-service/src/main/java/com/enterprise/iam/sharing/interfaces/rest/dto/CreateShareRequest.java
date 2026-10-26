package com.enterprise.iam.sharing.interfaces.rest.dto;

import com.enterprise.iam.sharing.domain.model.ShareScopeType;
import com.enterprise.iam.sharing.domain.model.ShareTargetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Frozen OpenAPI {@code CreateShareRequest} (phase-03 sharing/file contract
 * + V2.0 optional scopeType, folderPath, collectionResourceIds).
 */
public record CreateShareRequest(
        @NotNull @Positive Long resourceId,
        @NotBlank @Size(max = 128) String resourceInstanceKey,
        @NotNull @Valid Target target,
        @NotEmpty @Size(max = 100) List<@Positive Long> operationIds,
        @NotNull @Size(max = 1000) @Valid List<FieldGrant> fields,
        @NotNull Instant expireAt,
        boolean canReshare,
        ShareScopeType scopeType,
        @Size(max = 512) String folderPath,
        @Size(max = 500) List<@NotBlank String> collectionResourceIds) {

    public CreateShareRequest {
        scopeType = scopeType == null ? ShareScopeType.INSTANCE : scopeType;
        collectionResourceIds = collectionResourceIds == null
                ? List.of() : List.copyOf(collectionResourceIds);
    }

    public CreateShareRequest(
            Long resourceId,
            String resourceInstanceKey,
            Target target,
            List<Long> operationIds,
            List<FieldGrant> fields,
            Instant expireAt,
            boolean canReshare) {
        this(resourceId, resourceInstanceKey, target, operationIds, fields,
                expireAt, canReshare, ShareScopeType.INSTANCE, null, List.of());
    }

    public record Target(
            @NotNull ShareTargetType type,
            @NotNull @Positive Long id) {
    }

    public record FieldGrant(
            @NotNull @Positive Long operationId,
            @NotNull @Positive Long fieldId,
            boolean readable,
            boolean writable,
            boolean hidden,
            @Positive Long maskStrategyId) {
    }
}
