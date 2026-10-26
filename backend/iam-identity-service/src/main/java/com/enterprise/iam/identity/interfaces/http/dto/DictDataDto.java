package com.enterprise.iam.identity.interfaces.http.dto;

import com.enterprise.iam.common.web.json.NumericLong;

import java.time.Instant;

public record DictDataDto(
        long id,
        long tenantId,
        String dictType,
        String dictLabel,
        String dictValue,
        int sortOrder,
        String status,
        String cssTag,
        boolean isDefault,
        String description,
        @NumericLong long version,
        Instant createdAt,
        Instant updatedAt
) {}
