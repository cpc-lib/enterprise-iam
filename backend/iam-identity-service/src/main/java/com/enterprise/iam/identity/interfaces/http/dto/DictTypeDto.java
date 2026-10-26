package com.enterprise.iam.identity.interfaces.http.dto;

import com.enterprise.iam.common.web.json.NumericLong;

import java.time.Instant;

public record DictTypeDto(
        long id,
        long tenantId,
        String dictType,
        String dictName,
        String status,
        String description,
        @NumericLong long version,
        Instant createdAt,
        Instant updatedAt
) {}
