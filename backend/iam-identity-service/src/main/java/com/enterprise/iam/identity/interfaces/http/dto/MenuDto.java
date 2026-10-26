package com.enterprise.iam.identity.interfaces.http.dto;

import com.enterprise.iam.common.web.json.NumericLong;

import java.time.Instant;
import java.util.List;

public record MenuDto(
        long id,
        long tenantId,
        Long parentId,
        String menuKey,
        String code,
        String title,
        String path,
        String icon,
        int sortOrder,
        String status,
        String publishStatus,
        String permissionCode,
        String permission,
        String component,
        String menuType,
        boolean visible,
        @NumericLong long version,
        Instant createdAt,
        Instant updatedAt,
        List<MenuDto> children
) {}
