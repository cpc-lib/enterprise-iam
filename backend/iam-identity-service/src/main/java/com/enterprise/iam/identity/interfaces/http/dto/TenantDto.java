package com.enterprise.iam.identity.interfaces.http.dto;

import com.enterprise.iam.common.web.json.NumericLong;

import java.time.Instant;

public record TenantDto(
        String id,
        String tenantCode,
        String tenantName,
        String status,
        @NumericLong long version,
        Instant createdAt,
        Instant updatedAt) {
}
