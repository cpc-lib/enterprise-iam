package com.enterprise.iam.identity.interfaces.http.dto;

import com.enterprise.iam.common.web.json.NumericLong;

import java.time.Instant;

public record UserDetailDto(
        long id,
        long userId,
        long tenantId,
        String username,
        String displayName,
        String realName,
        String email,
        String phone,
        Long departmentId,
        Long positionId,
        String status,
        @NumericLong long version,
        Instant createdAt,
        Instant updatedAt,
        Boolean isTenantAdmin
) {
    public UserDetailDto(
            long id,
            long userId,
            long tenantId,
            String username,
            String displayName,
            String realName,
            String email,
            String phone,
            Long departmentId,
            Long positionId,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this(id, userId, tenantId, username, displayName, realName, email, phone,
                departmentId, positionId, status, version, createdAt, updatedAt, false);
    }
}
