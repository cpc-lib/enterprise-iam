package com.enterprise.iam.identity.interfaces.http.dto;

import com.enterprise.iam.common.web.json.NumericLong;

import java.time.Instant;

public record RoleDto(
        String id,
        String tenantId,
        String roleCode,
        String roleName,
        String description,
        String status,
        @NumericLong long version,
        int priority,
        Instant createdAt) {
}
