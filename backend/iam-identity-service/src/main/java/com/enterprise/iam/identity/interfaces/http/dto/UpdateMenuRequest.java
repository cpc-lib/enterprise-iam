package com.enterprise.iam.identity.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateMenuRequest(
        Long parentId,
        @Pattern(regexp = "^[A-Za-z0-9_-]{2,64}$") String menuKey,
        String code,
        @NotBlank String title,
        String path,
        String icon,
        Integer sortOrder,
        String permissionCode,
        String permission,
        String component,
        Boolean visible,
        Long version,
        String menuType
) {
    public String effectiveMenuKey() {
        if (menuKey != null && !menuKey.isBlank()) {
            return menuKey;
        }
        if (code != null && !code.isBlank()) {
            return code;
        }
        return "menu-" + System.currentTimeMillis();
    }

    public String effectivePermissionCode() {
        if (permissionCode != null && !permissionCode.isBlank()) {
            return permissionCode;
        }
        return permission;
    }

    public int effectiveSortOrder() {
        return sortOrder != null ? sortOrder : 0;
    }

    public boolean effectiveVisible() {
        return visible != null ? visible : true;
    }
}
